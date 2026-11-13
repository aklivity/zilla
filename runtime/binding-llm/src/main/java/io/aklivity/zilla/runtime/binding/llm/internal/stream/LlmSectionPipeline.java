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
    private int consumedLength;
    private int held = LlmEdgeTrim.BEFORE;
    private Output edgeTarget;
    private Output edgeOutput;
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
        held = LlmEdgeTrim.BEFORE;
    }

    boolean feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        Output output)
    {
        return feedWindow(buffer, offset, length, last, output);
    }

    // Forwards what the pipeline produces without its first and last bytes, such as the braces of an object. The
    // state of the trim is one int, which begin() resets, so the stages that need it pay for it per pipeline.
    boolean feedEdges(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        Output output)
    {
        if (edgeOutput == null)
        {
            edgeOutput = this::putEdges;
        }

        edgeTarget = output;

        return feedWindow(buffer, offset, length, last, edgeOutput);
    }

    // The caller owns the input and its offsets. The window [offset, limit) is presented again, unchanged, after
    // SUSPENDED, which is returned when the output limit is used up or the output buffer could not hold the next
    // piece. After STARVED the caller presents the bytes from offset + consumed() at the front of the next window.
    Status advance(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last,
        Output output,
        int outputLimit)
    {
        Status status;

        if (completed)
        {
            if (last && !closed)
            {
                output.put(outputBuffer, 0, 0, true);
                closed = true;
            }

            consumedLength = limit - offset;
            status = Status.COMPLETED;
        }
        else
        {
            int progress = offset;
            int remaining = outputLimit;
            int produced;

            do
            {
                final JsonPipelineResult result = pipeline.transform(buffer, progress, limit, last,
                    outputBuffer, 0, Math.min(outputBuffer.capacity(), remaining));
                status = result.status();
                progress += result.consumed();
                produced = result.produced();
                remaining -= produced;

                final boolean done = status == Status.COMPLETED && last;

                if (produced > 0 || done)
                {
                    output.put(outputBuffer, 0, produced, done);
                    closed |= done;
                }
            }
            while (status == Status.SUSPENDED && produced > 0 && remaining > 0);

            completed = status == Status.COMPLETED;
            consumedLength = switch (status)
            {
            case STARVED, ADVANCED -> progress - offset;
            case SUSPENDED -> 0;
            default -> limit - offset;
            };
        }

        return status;
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

    int consumed()
    {
        return consumedLength;
    }

    private void putEdges(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        held = LlmEdgeTrim.put(held, edgeTarget, buffer, offset, length, last);
    }

    // Copies the input after the bytes kept from the previous feed into a window of this pipeline, so an output
    // callback that re-wraps the caller's buffer cannot disturb what the parser has not consumed.
    private boolean feedWindow(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last,
        Output output)
    {
        final int windowLength = tailLength + length;

        reserve(windowLength);
        buffer.getBytes(offset, window, tailLength, length);
        windowBuffer.wrap(window, 0, windowLength);

        final Status status = advance(windowBuffer, 0, windowLength, last, output, Integer.MAX_VALUE);

        tailLength = status == Status.STARVED || status == Status.ADVANCED ? windowLength - consumedLength : 0;

        if (tailLength > 0)
        {
            System.arraycopy(window, consumedLength, window, 0, tailLength);
        }

        return status != Status.REJECTED;
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
