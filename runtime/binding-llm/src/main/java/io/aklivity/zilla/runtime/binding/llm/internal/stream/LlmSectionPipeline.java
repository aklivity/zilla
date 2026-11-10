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
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

final class LlmSectionPipeline
{
    private static final DirectBufferEx NO_INPUT = new UnsafeBufferEx(new byte[0]);

    @FunctionalInterface
    interface Output
    {
        void put(
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last);
    }

    private final LlmSectionEnvelope envelope;
    private final JsonPipeline pipeline;
    private final MutableDirectBufferEx outputBuffer;
    private final UnsafeBufferEx windowBuffer;

    private byte[] window;
    private int tailLength;
    private int unconsumedLength;
    private boolean completed;
    private boolean closed;

    LlmSectionPipeline(
        JsonTransform stage,
        MutableDirectBufferEx outputBuffer)
    {
        this.envelope = new LlmSectionEnvelope();
        this.outputBuffer = outputBuffer;
        this.windowBuffer = new UnsafeBufferEx();
        this.window = new byte[0];
        this.pipeline = JsonEx.stream(JsonEx.createParser())
            .envelope(envelope)
            .transform(stage)
            .into(JsonEx.createGenerator(), Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED));
    }

    LlmSectionPipeline(
        JsonTransform stage,
        MutableDirectBufferEx outputBuffer,
        JsonParserEx source)
    {
        this.envelope = new LlmSectionEnvelope();
        this.outputBuffer = outputBuffer;
        this.windowBuffer = null;
        this.window = null;
        this.pipeline = JsonEx.stream(JsonEx.createValueParser(source))
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
        closed = false;
    }

    boolean feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        Output output)
    {
        return feed(buffer, offset, length, last, output, true);
    }

    // The bytes the parser has not consumed are kept for the next feed unless the caller holds them, in which
    // case unconsumed() reports how many of the last bytes fed it should present again.
    boolean feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        Output output,
        boolean retain)
    {
        final int windowLength = tailLength + length;
        unconsumedLength = 0;

        if (completed)
        {
            if (last && !closed)
            {
                output.put(outputBuffer, 0, 0, true);
                closed = true;
            }

            return true;
        }

        DirectBufferEx input = buffer;
        int progress = offset;
        int limit = offset + length;

        if (tailLength > 0)
        {
            reserve(windowLength);
            buffer.getBytes(offset, window, tailLength, length);
            windowBuffer.wrap(window, 0, windowLength);

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
            progress += result.consumed();

            // the input may be a pool slot's shared buffer, which the output callback can re-wrap onto another
            // slot, so what the parser has not consumed is kept before the output is put
            unconsumedLength = status == Status.STARVED || status == Status.ADVANCED ? limit - progress : 0;
            tailLength = retain ? unconsumedLength : 0;
            retain(input, progress);

            final boolean done = status == Status.COMPLETED && last;

            if (result.produced() > 0 || done)
            {
                output.put(outputBuffer, 0, result.produced(), done);
                closed |= done;
            }
        }
        while (status == Status.SUSPENDED);

        completed = status == Status.COMPLETED;

        return status != Status.REJECTED;
    }

    // Drives the next value of the source parser this pipeline was built over, which keeps the window, the
    // bytes not yet consumed and any pushed-back fragment, so nothing is held here between calls.
    boolean pump(
        Output output)
    {
        Status status;

        do
        {
            final JsonPipelineResult result = pipeline.transform(NO_INPUT, 0, 0, false,
                outputBuffer, 0, outputBuffer.capacity());
            status = result.status();

            final boolean done = status == Status.COMPLETED;

            if (result.produced() > 0 || done)
            {
                output.put(outputBuffer, 0, result.produced(), done);
            }
        }
        while (status == Status.SUSPENDED);

        completed = status == Status.COMPLETED;

        return status != Status.REJECTED;
    }

    boolean completed()
    {
        return completed;
    }

    int unconsumed()
    {
        return unconsumedLength;
    }

    private void retain(
        DirectBufferEx input,
        int progress)
    {
        if (tailLength > 0)
        {
            if (input == windowBuffer)
            {
                System.arraycopy(window, progress, window, 0, tailLength);
            }
            else
            {
                reserve(tailLength);
                input.getBytes(progress, window, 0, tailLength);
            }
        }
    }

    private void reserve(
        int capacity)
    {
        if (window.length < capacity)
        {
            window = Arrays.copyOf(window, capacity);
        }
    }
}
