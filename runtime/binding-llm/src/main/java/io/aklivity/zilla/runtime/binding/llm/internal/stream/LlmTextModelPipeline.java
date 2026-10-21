/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.engine.buffer.BufferPool.NO_SLOT;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

// A section is a `{"text":…}` object. Its `text` value is decoded on arrival and presented to the chain as one
// continuous text value, and what the chain produces is written back as the escaped text value. Every other byte
// of the object is relayed unmodified and in order: the bytes before the text value as they arrive, and the bytes
// after it once the chain has finished the text. A chain that needs context across fragments retains it itself.
final class LlmTextModelPipeline implements LlmGuardPipeline
{
    private static final int ESCAPE_EXPANSION = 6;
    private static final DirectBufferEx EMPTY_TEXT = new UnsafeBufferEx(new byte[0]);

    private final ModelPipeline pipeline;
    private final BufferPool pool;
    private final long streamId;
    private final MutableDirectBufferEx transformBuffer;
    private final LlmTextPipeline decoder;
    private final LlmTextObjectScanner scanner;
    private final JsonGeneratorEx generator;
    private final MutableDirectBufferEx primerBuffer;
    private final ModelPipelineResult result;

    private int slot;
    private MutableDirectBufferEx slotBuffer;
    private int textCapacity;
    private int textLength;
    private int textProgress;
    private boolean started;
    private int textProduced;
    private ModelStatus textStatus;
    private int values;

    LlmTextModelPipeline(
        ModelPipeline pipeline,
        BufferPool pool,
        long streamId,
        MutableDirectBufferEx transformBuffer,
        boolean optional)
    {
        this.pipeline = pipeline;
        this.pool = pool;
        this.streamId = streamId;
        this.transformBuffer = transformBuffer;
        this.slot = NO_SLOT;
        this.decoder = new LlmTextPipeline(this::onText, optional);
        this.scanner = new LlmTextObjectScanner();
        this.generator = JsonEx.createGenerator();
        this.primerBuffer = new UnsafeBufferEx(new byte[16]);
        this.result = new ModelPipelineResult();
        begin();
    }

    static boolean objectShaped(
        LlmSectionType type)
    {
        return type == LlmSectionType.SYSTEM_INSTRUCTION || type == LlmSectionType.USER_TEXT ||
            type == LlmSectionType.ASSISTANT_TEXT || type == LlmSectionType.ASSISTANT_REFUSAL ||
            type == LlmSectionType.ASSISTANT_REASONING || type == LlmSectionType.TOOL_RESULT;
    }

    @Override
    public void reset()
    {
        pipeline.reset();
        releaseSlot();
        begin();
    }

    @Override
    public ModelPipelineResult accept(
        long traceId,
        long bindingId,
        long authorization,
        boolean sectionInit,
        boolean sectionFin,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        int position = srcIndex;
        int produced = 0;
        ModelStatus status = ModelStatus.UNDERFLOW;
        boolean stopped = false;

        values = 0;

        while (!stopped && position < srcLimit)
        {
            final int region = scanner.region();

            if (region == LlmTextObjectScanner.TEXT)
            {
                final int end = scanner.scan(src, position, srcLimit);

                if (!feed(src, position, end - position))
                {
                    status = ModelStatus.REJECTED;
                    stopped = true;
                }

                position = end;
            }
            else if (region == LlmTextObjectScanner.SUFFIX)
            {
                decoder.feed(src, position, 1, false);
                finishText(traceId, bindingId, authorization, dst, dstIndex + produced, dstLimit);

                produced += textProduced;
                status = textStatus;
                stopped = status == ModelStatus.OVERFLOW || status == ModelStatus.REJECTED || status == ModelStatus.SUSPENDED;
            }
            else
            {
                final int limit = Math.min(srcLimit, position + dstLimit - dstIndex - produced);
                final int end = scanner.scan(src, position, limit);

                dst.putBytes(dstIndex + produced, src, position, end - position);
                produced += end - position;

                if (region == LlmTextObjectScanner.PREFIX && !feed(src, position, end - position))
                {
                    status = ModelStatus.REJECTED;
                    stopped = true;
                }

                position = end;
                stopped |= position == limit && limit < srcLimit;
            }
        }

        if (!stopped && scanner.region() != LlmTextObjectScanner.TAIL && textLength > textProgress)
        {
            transformText(traceId, bindingId, authorization, 0, dst, dstIndex + produced, dstLimit);

            produced += textProduced;
            status = textStatus;
        }

        if (position < srcLimit && status != ModelStatus.REJECTED && status != ModelStatus.SUSPENDED)
        {
            status = ModelStatus.OVERFLOW;
        }
        else if (sectionFin && !stopped && status != ModelStatus.REJECTED && status != ModelStatus.OVERFLOW)
        {
            if (scanner.region() != LlmTextObjectScanner.TAIL)
            {
                finishText(traceId, bindingId, authorization, dst, dstIndex + produced, dstLimit);

                produced += textProduced;
                status = textStatus;
            }
            else
            {
                status = ModelStatus.COMPLETE;
            }
        }

        return result.set(status, position - srcIndex, produced);
    }

    @Override
    public ModelPipelineResult finish(
        long traceId,
        long bindingId,
        long authorization,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        int produced = 0;
        ModelStatus status = ModelStatus.COMPLETE;

        values = 0;

        if (scanner.region() != LlmTextObjectScanner.TAIL)
        {
            finishText(traceId, bindingId, authorization, dst, dstIndex, dstLimit);

            produced = textProduced;
            status = textStatus;
        }

        return result.set(status, 0, produced);
    }

    @Override
    public boolean finishes()
    {
        return true;
    }

    @Override
    public boolean identity()
    {
        return pipeline.identity();
    }

    @Override
    public int values()
    {
        return values;
    }

    @Override
    public int closeWidth()
    {
        return 0;
    }

    @Override
    public int close(
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        return 0;
    }

    private void begin()
    {
        decoder.begin();
        scanner.begin();
        textLength = 0;
        textProgress = 0;
        started = false;

        generator.wrap(primerBuffer, 0, primerBuffer.capacity());
        generator.reset();
        generator.writeStartObject().writeKey("text").writeStartEscaped();
    }

    private boolean feed(
        DirectBufferEx src,
        int offset,
        int length)
    {
        return length == 0 ||
            acquireSlot() && textLength + length <= textCapacity && decoder.feed(src, offset, length, false);
    }

    private void finishText(
        long traceId,
        long bindingId,
        long authorization,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        transformText(traceId, bindingId, authorization, Flags.FIN, dst, dstIndex, dstLimit);

        if (textStatus != ModelStatus.OVERFLOW && textStatus != ModelStatus.REJECTED && textStatus != ModelStatus.SUSPENDED)
        {
            scanner.settle();
        }
    }

    private void transformText(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final int innerFlags = started ? flags : flags | Flags.INIT;
        final int room = Math.min(Math.max(dstLimit - dstIndex, 0) / ESCAPE_EXPANSION, transformBuffer.capacity());

        final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, innerFlags,
            slot != NO_SLOT ? slotBuffer : EMPTY_TEXT, textProgress, textLength, transformBuffer, 0, room);

        textProgress += inner.consumed();
        values += inner.produced();
        started = true;
        textStatus = inner.status();
        textProduced = embed(inner.produced(), dst, dstIndex, dstLimit);

        compact();
    }

    private int embed(
        int length,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        generator.wrap(dst, dstIndex, dstLimit);

        if (length > 0)
        {
            generator.writeSegment(transformBuffer, 0, length);
        }

        return generator.length();
    }

    private boolean acquireSlot()
    {
        if (slot == NO_SLOT)
        {
            slot = pool.acquire(streamId);

            if (slot != NO_SLOT)
            {
                slotBuffer = pool.buffer(slot);
                textCapacity = pool.slotCapacity();
            }
        }

        return slot != NO_SLOT;
    }

    private void releaseSlot()
    {
        if (slot != NO_SLOT)
        {
            pool.release(slot);
            slot = NO_SLOT;
            slotBuffer = null;
        }
    }

    private void compact()
    {
        if (textProgress == textLength)
        {
            textProgress = 0;
            textLength = 0;
        }
        else if (textProgress > 0)
        {
            final int remaining = textLength - textProgress;

            for (int index = 0; index < remaining; index++)
            {
                slotBuffer.putByte(index, slotBuffer.getByte(textProgress + index));
            }

            textLength = remaining;
            textProgress = 0;
        }
    }

    private void onText(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        slotBuffer.putBytes(textLength, buffer, offset, length);
        textLength += length;
    }
}
