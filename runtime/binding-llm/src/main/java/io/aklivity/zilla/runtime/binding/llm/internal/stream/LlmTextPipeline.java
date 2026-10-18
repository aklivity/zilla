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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmTextPipeline
{
    @FunctionalInterface
    interface Output
    {
        void put(
            DirectBufferEx buffer,
            int offset,
            int length);
    }

    private final JsonPipeline pipeline;
    private final UnsafeBufferEx windowBuffer;

    private byte[] window;
    private int tailLength;
    private boolean completed;

    LlmTextPipeline(
        Output output)
    {
        this.windowBuffer = new UnsafeBufferEx();
        this.window = new byte[0];
        this.pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new LlmTextTransform())
            .into(new LlmTextSink(output));
    }

    void begin()
    {
        pipeline.reset();
        tailLength = 0;
        completed = false;
    }

    boolean feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        Status status = Status.COMPLETED;

        if (!completed)
        {
            final int windowLength = tailLength + length;

            DirectBufferEx input = buffer;
            int from = offset;
            int limit = offset + length;

            if (tailLength > 0)
            {
                reserve(windowLength);
                buffer.getBytes(offset, window, tailLength, length);
                windowBuffer.wrap(window, 0, windowLength);

                input = windowBuffer;
                from = 0;
                limit = windowLength;
            }

            status = pipeline.transform(input, from, limit, last);

            completed = status == Status.COMPLETED;
            tailLength = status == Status.STARVED ? pipeline.remaining() : 0;

            if (tailLength > 0)
            {
                if (input == windowBuffer)
                {
                    System.arraycopy(window, limit - tailLength, window, 0, tailLength);
                }
                else
                {
                    reserve(tailLength);
                    input.getBytes(limit - tailLength, window, 0, tailLength);
                }
            }
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
