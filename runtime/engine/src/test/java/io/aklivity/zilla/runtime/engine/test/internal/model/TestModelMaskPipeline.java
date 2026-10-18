/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.test.internal.model;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.List;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.util.Flags;

// Length-preserving streaming transform that replaces each occurrence of a configured word with '*' of the
// same width. A word can straddle fragments, so the last (longest word - 1) bytes of the value seen so far are
// retained until more input arrives or the value completes; the output therefore lags the input by that overlap.
final class TestModelMaskPipeline implements ModelPipeline
{
    private final ModelPipelineResult result;
    private final byte[][] words;
    private final int overlap;

    private byte[] input;
    private int inputLength;
    private byte[] output;
    private int outputLength;
    private int outputDrained;

    TestModelMaskPipeline(
        List<String> mask)
    {
        this.result = new ModelPipelineResult();
        this.words = mask.stream().map(word -> word.getBytes(UTF_8)).toArray(byte[][]::new);
        this.overlap = Arrays.stream(words).mapToInt(word -> word.length).max().orElse(1) - 1;
        this.input = new byte[0];
        this.output = new byte[0];
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

        if (consumed > 0)
        {
            append(src, srcIndex, consumed);
            mask(Flags.hasFin(flags));
        }
        else if (Flags.hasFin(flags) && inputLength > 0)
        {
            mask(true);
        }

        final int produced = Math.min(outputLength - outputDrained, dstLimit - dstIndex);

        dst.putBytes(dstIndex, output, outputDrained, produced);
        outputDrained += produced;

        final boolean drained = outputDrained == outputLength;
        final ModelStatus status = !drained ? ModelStatus.OVERFLOW
            : Flags.hasFin(flags) ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;

        if (drained)
        {
            outputLength = 0;
            outputDrained = 0;
        }

        return result.set(status, consumed, produced);
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }

    @Override
    public void reset()
    {
        inputLength = 0;
        outputLength = 0;
        outputDrained = 0;
    }

    private void append(
        DirectBufferEx src,
        int srcIndex,
        int length)
    {
        if (input.length < inputLength + length)
        {
            input = Arrays.copyOf(input, inputLength + length);
        }

        src.getBytes(srcIndex, input, inputLength, length);
        inputLength += length;
    }

    private void mask(
        boolean fin)
    {
        final int safe = fin ? inputLength : Math.max(inputLength - overlap, 0);

        if (output.length < outputLength + inputLength)
        {
            output = Arrays.copyOf(output, outputLength + inputLength);
        }

        int index = 0;

        while (index < safe)
        {
            final int length = matched(index);

            if (length > 0)
            {
                Arrays.fill(output, outputLength, outputLength + length, (byte) '*');
                outputLength += length;
                index += length;
            }
            else
            {
                output[outputLength++] = input[index++];
            }
        }

        System.arraycopy(input, index, input, 0, inputLength - index);
        inputLength -= index;
    }

    private int matched(
        int index)
    {
        int length = 0;

        for (int word = 0; length == 0 && word < words.length; word++)
        {
            final byte[] candidate = words[word];

            if (index + candidate.length <= inputLength &&
                Arrays.equals(input, index, index + candidate.length, candidate, 0, candidate.length))
            {
                length = candidate.length;
            }
        }

        return length;
    }
}
