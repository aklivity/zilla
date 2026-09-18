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
 * Each {@code encodeEventName}/{@code encodeData}/{@code encodeFlush} call writes one complete SSE
 * field line (or the blank line terminating an event). A caller must sequence a full event as
 * {@code encodeEventName} (writing {@code event:} when present) followed by {@code encodeData}
 * (writing {@code data:}) followed by {@code encodeFlush} (writing {@code id:} and the terminating
 * blank line) so the wire form matches the field order every real SSE sender uses, since the event
 * name, when present, always precedes its data on the wire. A single data chunk is written as one
 * {@code data:} line; splitting embedded newlines across multiple {@code data:} lines is not
 * implemented here.
 * </p>
 */
public final class LlmSseContentEncoder implements LlmContentEncoder
{
    private static final String DATA_FIELD = "data: ";
    private static final String EVENT_FIELD = "event: ";
    private static final String ID_FIELD = "id: ";
    private static final byte LF = '\n';

    @Override
    public int encodeEventName(
        String event,
        MutableDirectBuffer encoded,
        int encodedOffset,
        int encodedLimit)
    {
        int written = 0;

        if (event != null)
        {
            int required = EVENT_FIELD.length() + event.length() + 1;
            if (encodedOffset + required <= encodedLimit)
            {
                int position = encodedOffset;
                position += encoded.putStringWithoutLengthUtf8(position, EVENT_FIELD);
                position += encoded.putStringWithoutLengthUtf8(position, event);
                encoded.putByte(position, LF);
                position++;
                written = position - encodedOffset;
            }
        }

        return written;
    }

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
        DirectBuffer id,
        int idOffset,
        int idLength,
        MutableDirectBuffer encoded,
        int encodedOffset,
        int encodedLimit)
    {
        int required = 1 + (idLength > 0 ? ID_FIELD.length() + idLength + 1 : 0);
        int written = 0;

        if (encodedOffset + required <= encodedLimit)
        {
            int position = encodedOffset;
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
