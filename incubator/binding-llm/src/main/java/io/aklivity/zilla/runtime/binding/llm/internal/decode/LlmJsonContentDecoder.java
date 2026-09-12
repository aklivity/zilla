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
package io.aklivity.zilla.runtime.binding.llm.internal.decode;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes {@code application/json} framing: a non-streaming response has no event boundaries
 * of its own, so the entire document is dispatched as a single event, the same abstraction
 * {@code text/event-stream} dispatches one event per SSE frame through.
 * <p>
 * Unlike SSE framing, a JSON document carries no self-contained marker of where it ends, so this
 * decoder cannot tell a document is complete from its bytes alone: each {@link #decode} call with
 * a non-empty range simply forwards those bytes, arriving in as many fragments as the network
 * delivers them in; the single terminal event is only reported on the empty-range call made once
 * the stream has actually ended.
 * </p>
 */
final class LlmJsonContentDecoder implements LlmContentDecoder
{
    private static final DirectBuffer EMPTY_ID = new UnsafeBuffer(new byte[0]);

    @Override
    public int decode(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output)
    {
        if (limit > offset)
        {
            output.data(buffer, offset, limit - offset);
        }
        else
        {
            output.flush(null, EMPTY_ID, 0, 0);
        }

        return limit;
    }
}
