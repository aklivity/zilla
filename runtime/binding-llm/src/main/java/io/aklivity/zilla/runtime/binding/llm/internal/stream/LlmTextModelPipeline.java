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
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelRejection;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

// Each section of the run is a `{"text":…}` object, decoded on arrival and presented to the chain as one continuous
// text value. The chain is fed all but the trailing overlap bytes of what has arrived, so a pattern straddling two
// deltas is seen whole; what the chain produces for the bytes it was fed is embedded in an object of the section being
// delivered, and the held bytes are fed when more text arrives or the run ends.
final class LlmTextModelPipeline implements LlmRunPipeline
{
    private static final byte[] PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] SUFFIX = "\"}".getBytes(UTF_8);
    private static final byte COMMA = ',';
    private static final int ESCAPE_EXPANSION = 6;
    private static final int RESIDUAL_CHUNK = 256;
    private static final int RESIDUAL_EMPTY = 2;
    private static final DirectBufferEx EMPTY_TEXT = new UnsafeBufferEx(new byte[0]);

    private final ModelPipeline pipeline;
    private final int overlap;
    private final BufferPool pool;
    private final long streamId;
    private final MutableDirectBufferEx transformBuffer;
    private final LlmTextPipeline decoder;
    private final JsonGeneratorEx escaper;
    private final ModelPipelineResult result;
    private final LlmSectionPipeline residual;
    private final LlmSectionPipeline.Output residualOutput;
    private final LlmSectionAttributes residualAttributes;

    private int slot;
    private MutableDirectBufferEx slotBuffer;
    private int textCapacity;
    private int residualLength;
    private int textLength;
    private int textProgress;
    private int emitted;
    private boolean started;

    LlmTextModelPipeline(
        ModelPipeline pipeline,
        int overlap,
        BufferPool pool,
        long streamId,
        MutableDirectBufferEx transformBuffer)
    {
        this.pipeline = pipeline;
        this.overlap = overlap;
        this.pool = pool;
        this.streamId = streamId;
        this.transformBuffer = transformBuffer;
        this.slot = NO_SLOT;
        this.residual = new LlmSectionPipeline(new LlmTextResidualTransform(), new UnsafeBufferEx(new byte[RESIDUAL_CHUNK]));
        this.residualOutput = (buffer, offset, length, last) -> onResidual(buffer, offset, length);
        this.residualAttributes = new LlmSectionAttributes();
        this.decoder = new LlmTextPipeline(this::onText);
        this.escaper = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        this.result = new ModelPipelineResult();
        decoder.begin();
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
        decoder.begin();
        releaseSlot();
        textLength = 0;
        textProgress = 0;
        residualLength = 0;
        emitted = 0;
        started = false;
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
        final int consumed = srcLimit - srcIndex;

        if (sectionInit)
        {
            decoder.begin();
            residualLength = 0;
            residual.begin(residualAttributes);
        }

        ModelPipelineResult outcome = null;

        if (consumed > 0 && !accepted(src, srcIndex, consumed, sectionFin))
        {
            outcome = result.set(ModelStatus.REJECTED, consumed, 0);
        }
        else
        {
            outcome = run(traceId, bindingId, authorization, 0, feedLimit(false), consumed, dst, dstIndex, dstLimit);
        }

        return outcome;
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
        return run(traceId, bindingId, authorization, Flags.FIN, feedLimit(true), 0, dst, dstIndex, dstLimit);
    }

    @Override
    public int closeWidth()
    {
        return PREFIX.length + SUFFIX.length + (residualLength > RESIDUAL_EMPTY ? residualLength : 0);
    }

    @Override
    public int close(
        MutableDirectBufferEx dst,
        int dstIndex)
    {
        int position = dstIndex;

        if (emitted == 0)
        {
            dst.putBytes(position, PREFIX);
            position += PREFIX.length;
        }

        dst.putByte(position++, SUFFIX[0]);

        if (residualLength > RESIDUAL_EMPTY)
        {
            dst.putByte(position++, COMMA);
            dst.putBytes(position, slotBuffer, textCapacity + 1, residualLength - RESIDUAL_EMPTY);
            position += residualLength - RESIDUAL_EMPTY;
        }

        dst.putByte(position++, SUFFIX[1]);

        emitted = 0;
        residualLength = 0;

        return position - dstIndex;
    }

    private boolean accepted(
        DirectBufferEx src,
        int srcIndex,
        int length,
        boolean sectionFin)
    {
        return acquireSlot() && textLength + length <= textCapacity && residualLength + length <= residualCapacity() &&
            decoder.feed(src, srcIndex, length, sectionFin) &&
            residual.feed(src, srcIndex, length, sectionFin, residualOutput);
    }

    private boolean acquireSlot()
    {
        if (slot == NO_SLOT)
        {
            slot = pool.acquire(streamId);

            if (slot != NO_SLOT)
            {
                slotBuffer = pool.buffer(slot);
                textCapacity = pool.slotCapacity() / 2;
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

    private int residualCapacity()
    {
        return pool.slotCapacity() - textCapacity;
    }

    private void onResidual(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        slotBuffer.putBytes(textCapacity + residualLength, buffer, offset, length);
        residualLength += length;
    }

    private ModelPipelineResult run(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        int limit,
        int consumed,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final boolean fin = Flags.hasFin(flags);

        ModelStatus status = ModelStatus.UNDERFLOW;
        ModelRejection rejection = null;
        int produced = 0;

        if (limit > textProgress || fin)
        {
            final int innerFlags = started ? flags : flags | Flags.INIT;

            final int room = Math.min(Math.max(dstLimit - dstIndex - (emitted == 0 ? PREFIX.length : 0), 0) / ESCAPE_EXPANSION,
                transformBuffer.capacity());

            final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, innerFlags,
                slot != NO_SLOT ? slotBuffer : EMPTY_TEXT, textProgress, limit, transformBuffer, 0, room);

            status = inner.status();
            rejection = inner.rejection();
            textProgress += inner.consumed();
            started = true;
            produced = embed(inner.produced(), dst, dstIndex, dstLimit);

            compact();
        }

        return result.set(status, consumed, produced, rejection);
    }

    private int feedLimit(
        boolean flush)
    {
        int limit = textLength;

        if (!flush)
        {
            limit = Math.max(textLength - overlap, textProgress);

            while (limit > textProgress && limit < textLength && (slotBuffer.getByte(limit) & 0xC0) == 0x80)
            {
                limit--;
            }
        }

        return limit;
    }

    private int embed(
        int length,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        int position = dstIndex;

        if (length > 0)
        {
            if (emitted == 0)
            {
                dst.putBytes(position, PREFIX);
                position += PREFIX.length;
            }

            escaper.wrap(dst, position, dstLimit);
            escaper.writeSegment(transformBuffer, 0, length);
            position += escaper.length();
        }

        final int produced = position - dstIndex;

        emitted += produced;

        return produced;
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
