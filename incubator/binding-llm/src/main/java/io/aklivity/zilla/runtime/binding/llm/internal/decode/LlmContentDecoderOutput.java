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

/**
 * Receives the event frames produced by an {@link LlmContentDecoder} as it decodes content-type
 * framing from a stream's raw bytes.
 */
public interface LlmContentDecoderOutput
{
    /**
     * Emits content bytes belonging to the event currently being decoded.
     *
     * @param buffer  the buffer holding the content bytes
     * @param offset  the offset of the content bytes within {@code buffer}
     * @param length  the number of content bytes
     */
    void data(
        DirectBuffer buffer,
        int offset,
        int length);

    /**
     * Emits an event boundary reached by the decoder.
     *
     * @param event   the content-type-specific event name, or {@code null} when the content-type has none
     * @param buffer  the buffer holding any bytes associated with the event boundary
     * @param offset  the offset of the associated bytes within {@code buffer}
     * @param length  the number of associated bytes
     */
    void flush(
        String event,
        DirectBuffer buffer,
        int offset,
        int length);
}
