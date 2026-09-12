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
 * Encodes {@code text/event-stream} (SSE) framing, the inverse of {@code LlmSseContentDecoder}.
 * <p>
 * Each {@code encodeData}/{@code encodeFlush} call writes one complete SSE field line (or the blank
 * line terminating an event); a decoder on the receiving side parses fields independently of the
 * order they arrive in, so this need not reproduce a particular field ordering to round-trip
 * faithfully. A single data chunk is written as one {@code data:} line; splitting embedded newlines
 * across multiple {@code data:} lines is not implemented here.
 * </p>
 */
final class LlmSseContentEncoder implements LlmContentEncoder
{
    private static final String DATA_FIELD = "data: ";
    private static final String EVENT_FIELD = "event: ";
    private static final String ID_FIELD = "id: ";
    private static final byte LF = '\n';

    @Override
    public int encodeData(
        DirectBuffer buffer,
        int offset,
        int length,
        MutableDirectBuffer encoded,
        int encodedOffset,
        int encodedLimit)
    {
        int required = DATA_FIELD.length() + length + 1;
        int written = 0;

        if (encodedOffset + required <= encodedLimit)
        {
            int position = encodedOffset;
            position += encoded.putStringWithoutLengthUtf8(position, DATA_FIELD);
            encoded.putBytes(position, buffer, offset, length);
            position += length;
            encoded.putByte(position, LF);
            position++;
            written = position - encodedOffset;
        }

        return written;
    }

    @Override
    public int encodeFlush(
        String event,
        DirectBuffer id,
        int idOffset,
        int idLength,
        MutableDirectBuffer encoded,
        int encodedOffset,
        int encodedLimit)
    {
        int required = 1 +
            (event != null ? EVENT_FIELD.length() + event.length() + 1 : 0) +
            (idLength > 0 ? ID_FIELD.length() + idLength + 1 : 0);
        int written = 0;

        if (encodedOffset + required <= encodedLimit)
        {
            int position = encodedOffset;
            if (event != null)
            {
                position += encoded.putStringWithoutLengthUtf8(position, EVENT_FIELD);
                position += encoded.putStringWithoutLengthUtf8(position, event);
                encoded.putByte(position, LF);
                position++;
            }
            if (idLength > 0)
            {
                position += encoded.putStringWithoutLengthUtf8(position, ID_FIELD);
                encoded.putBytes(position, id, idOffset, idLength);
                position += idLength;
                encoded.putByte(position, LF);
                position++;
            }
            encoded.putByte(position, LF);
            position++;
            written = position - encodedOffset;
        }

        return written;
    }
}
