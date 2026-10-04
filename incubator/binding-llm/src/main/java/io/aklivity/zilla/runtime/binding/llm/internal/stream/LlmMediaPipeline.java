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

import java.util.Arrays;
import java.util.Map;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

final class LlmMediaPipeline
{
    private static final int MIN_WINDOW = 64;

    @FunctionalInterface
    interface Output
    {
        void put(
            DirectBufferEx buffer,
            int offset,
            int length);
    }

    private final LlmSectionEnvelope envelope;
    private final JsonPipeline pipeline;
    private final MutableDirectBufferEx outputBuffer;
    private final UnsafeBufferEx windowBuffer;

    private byte[] windowBytes;
    private byte[] tail;
    private int tailLength;
    private boolean completed;

    LlmMediaPipeline(
        JsonTransform stage,
        MutableDirectBufferEx outputBuffer)
    {
        this.envelope = new LlmSectionEnvelope();
        this.outputBuffer = outputBuffer;
        this.windowBuffer = new UnsafeBufferEx();
        this.windowBytes = new byte[0];
        this.tail = new byte[0];
        this.pipeline = JsonEx.stream(JsonEx.createParser())
            .envelope(envelope)
            .transform(stage)
            .into(JsonEx.createGenerator(), Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED));
    }

    void begin(
        LlmSectionAttributes attributes)
    {
        envelope.wrap(attributes);
        pipeline.reset();
        tailLength = 0;
        completed = false;
    }

    boolean feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        Output output)
    {
        final int windowLength = tailLength + length;

        if (completed)
        {
            return true;
        }

        if (!last && windowLength < MIN_WINDOW)
        {
            retain(buffer, offset, length);

            return true;
        }

        DirectBufferEx input = buffer;
        int progress = offset;
        int limit = offset + length;

        if (tailLength > 0)
        {
            if (windowBytes.length < windowLength)
            {
                windowBytes = new byte[windowLength];
            }

            System.arraycopy(tail, 0, windowBytes, 0, tailLength);
            buffer.getBytes(offset, windowBytes, tailLength, length);
            windowBuffer.wrap(windowBytes, 0, windowLength);

            input = windowBuffer;
            progress = 0;
            limit = windowLength;
        }

        Status status;

        do
        {
            final JsonPipelineResult result = pipeline.transform(input, progress, limit, last,
                outputBuffer, 0, outputBuffer.capacity());
            status = result.status();

            if (result.produced() > 0)
            {
                output.put(outputBuffer, 0, result.produced());
            }

            progress += result.consumed();
        }
        while (status == Status.SUSPENDED);

        completed = status == Status.COMPLETED;
        tailLength = status == Status.STARVED || status == Status.ADVANCED ? limit - progress : 0;

        if (tailLength > 0)
        {
            if (tail.length < tailLength)
            {
                tail = new byte[tailLength];
            }

            input.getBytes(progress, tail, 0, tailLength);
        }

        return status != Status.REJECTED;
    }

    private void retain(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (tail.length < tailLength + length)
        {
            tail = Arrays.copyOf(tail, tailLength + length);
        }

        buffer.getBytes(offset, tail, tailLength, length);
        tailLength += length;
    }
}
