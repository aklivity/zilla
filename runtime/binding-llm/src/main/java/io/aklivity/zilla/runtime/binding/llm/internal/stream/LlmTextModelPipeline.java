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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelRejection;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

final class LlmTextModelPipeline implements ModelPipeline
{
    private static final byte[] PREFIX = "{\"text\":\"".getBytes(UTF_8);
    private static final byte[] SUFFIX = "\"}".getBytes(UTF_8);
    private static final int ESCAPE_EXPANSION = 6;

    private final ModelPipeline pipeline;
    private final LlmTextPipeline decoder;
    private final JsonGeneratorEx escaper;
    private final ModelPipelineResult result;
    private final UnsafeBufferEx textBuffer;
    private final UnsafeBufferEx outputBuffer;

    private byte[] textBytes;
    private byte[] outputBytes;
    private int textLength;
    private int textProgress;
    private int emitted;

    LlmTextModelPipeline(
        ModelPipeline pipeline)
    {
        this.pipeline = pipeline;
        this.decoder = new LlmTextPipeline(this::onText);
        this.escaper = JsonEx.createGenerator(Map.of(JsonGeneratorEx.GENERATE_ESCAPED, true));
        this.result = new ModelPipelineResult();
        this.textBytes = new byte[0];
        this.outputBytes = new byte[0];
        this.textBuffer = new UnsafeBufferEx(textBytes);
        this.outputBuffer = new UnsafeBufferEx(outputBytes);
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
    public ModelPipelineResult transform(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        DirectBufferEx src,
        int srcIndex,
        int srcLimit,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        final int consumed = srcLimit - srcIndex;

        ModelStatus status = ModelStatus.REJECTED;
        ModelRejection rejection = null;
        int produced = 0;

        if (consumed == 0 || decoder.feed(src, srcIndex, consumed, Flags.hasFin(flags)))
        {
            if (pipeline.identity())
            {
                final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags,
                    textBuffer, textProgress, textLength, dst, dstIndex, dstLimit);

                status = inner.status();
                rejection = inner.rejection();
                textProgress += inner.consumed();
            }
            else
            {
                final int framing = (emitted == 0 ? PREFIX.length : 0) + SUFFIX.length;
                final int room = Math.max(dstLimit - dstIndex - framing, 0) / ESCAPE_EXPANSION;

                if (room > 0)
                {
                    reserveOutput(room);

                    final ModelPipelineResult inner = pipeline.transform(traceId, bindingId, authorization, flags,
                        textBuffer, textProgress, textLength, outputBuffer, 0, room);

                    status = inner.status();
                    rejection = inner.rejection();
                    textProgress += inner.consumed();

                    if (inner.produced() > 0 || status == ModelStatus.COMPLETE)
                    {
                        produced = embed(inner.produced(), status == ModelStatus.COMPLETE, dst, dstIndex, dstLimit);
                    }
                }
                else
                {
                    status = ModelStatus.OVERFLOW;
                }
            }

            compact();
        }

        return result.set(status, consumed, produced, rejection);
    }

    @Override
    public boolean identity()
    {
        return pipeline.identity();
    }

    @Override
    public boolean deterministic()
    {
        return pipeline.deterministic();
    }

    @Override
    public void reset()
    {
        pipeline.reset();
        decoder.begin();
        textLength = 0;
        textProgress = 0;
        emitted = 0;
    }

    private int embed(
        int length,
        boolean complete,
        MutableDirectBufferEx dst,
        int dstIndex,
        int dstLimit)
    {
        int position = dstIndex;

        if (emitted == 0)
        {
            dst.putBytes(position, PREFIX);
            position += PREFIX.length;
        }

        escaper.wrap(dst, position, dstLimit);
        escaper.writeSegment(outputBuffer, 0, length);
        position += escaper.length();

        if (complete)
        {
            dst.putBytes(position, SUFFIX);
            position += SUFFIX.length;
        }

        final int produced = position - dstIndex;

        emitted += produced;

        return produced;
    }

    private void reserveOutput(
        int capacity)
    {
        if (outputBytes.length < capacity)
        {
            outputBytes = new byte[capacity];
            outputBuffer.wrap(outputBytes);
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
            System.arraycopy(textBytes, textProgress, textBytes, 0, textLength - textProgress);
            textLength -= textProgress;
            textProgress = 0;
        }
    }

    private void onText(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (textBytes.length < textLength + length)
        {
            textBytes = Arrays.copyOf(textBytes, textLength + length);
            textBuffer.wrap(textBytes);
        }

        buffer.getBytes(offset, textBytes, textLength, length);
        textLength += length;
    }
}
