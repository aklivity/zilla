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
package io.aklivity.zilla.runtime.binding.llm.internal.encode;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Encodes a single stream's content-type framing, the inverse of {@code LlmContentDecoder}.
 * <p>
 * An instance is confined to one stream; {@link LlmContentEncoderSpi#supply()} creates a fresh
 * instance per stream so encode state is never shared.
 * </p>
 */
public interface LlmContentEncoder
{
    /**
     * Encodes the content bytes of the event currently being written into {@code encoded[encodedOffset, encodedLimit)}.
     *
     * @param buffer        the buffer holding the content bytes
     * @param offset        the offset of the content bytes within {@code buffer}
     * @param length        the number of content bytes
     * @param encoded       the destination buffer
     * @param encodedOffset the offset to write at within {@code encoded}
     * @param encodedLimit  the limit of the destination region within {@code encoded}
     * @return the number of bytes written, or {@code 0} when the destination region is too small
     */
    int encodeData(
        DirectBuffer buffer,
        int offset,
        int length,
        MutableDirectBuffer encoded,
        int encodedOffset,
        int encodedLimit);

    /**
     * Encodes an event boundary into {@code encoded[encodedOffset, encodedLimit)}.
     *
     * @param event         the content-type-specific event name, or {@code null} when the content-type has none
     * @param id            the buffer holding any bytes associated with the event boundary
     * @param idOffset      the offset of the associated bytes within {@code id}
     * @param idLength      the number of associated bytes
     * @param encoded       the destination buffer
     * @param encodedOffset the offset to write at within {@code encoded}
     * @param encodedLimit  the limit of the destination region within {@code encoded}
     * @return the number of bytes written, or {@code 0} when the destination region is too small
     */
    int encodeFlush(
        String event,
        DirectBuffer id,
        int idOffset,
        int idLength,
        MutableDirectBuffer encoded,
        int encodedOffset,
        int encodedLimit);
}
