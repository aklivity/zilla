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
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes {@code text/event-stream} (SSE) framing: blank-line-delimited events carrying
 * {@code data:}, {@code event:} and {@code id:} fields, per the WHATWG SSE parsing algorithm.
 * <p>
 * Framing decode only; the decoded {@code data} bytes are the raw SSE payload with no
 * dialect-specific (e.g. OpenAI, Anthropic) interpretation applied.
 * </p>
 */
final class LlmSseContentDecoder implements LlmContentDecoder
{
    private static final byte COLON = ':';
    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte SPACE = ' ';
    private static final byte NUL = 0x00;

    private static final String DATA_FIELD = "data";
    private static final String EVENT_FIELD = "event";
    private static final String ID_FIELD = "id";

    private static final DirectBuffer LINE_FEED = new UnsafeBuffer(new byte[] { LF });

    private byte[] eventIdBytes = new byte[0];
    private final MutableDirectBuffer eventId = new UnsafeBuffer(eventIdBytes);

    private int eventIdLength;
    private String eventName;
    private boolean dataFieldSeen;

    @Override
    public int decode(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output)
    {
        int progress = offset;
        int lineStart = offset;

        scan:
        while (lineStart < limit)
        {
            int contentEnd = -1;
            int nextLineStart = -1;

            for (int cursor = lineStart; cursor < limit; cursor++)
            {
                byte candidate = buffer.getByte(cursor);
                if (candidate == LF)
                {
                    contentEnd = cursor;
                    nextLineStart = cursor + 1;
                    break;
                }
                else if (candidate == CR)
                {
                    if (cursor + 1 >= limit)
                    {
                        break scan;
                    }
                    contentEnd = cursor;
                    nextLineStart = buffer.getByte(cursor + 1) == LF ? cursor + 2 : cursor + 1;
                    break;
                }
            }

            if (contentEnd < 0)
            {
                break;
            }

            onLine(buffer, lineStart, contentEnd, output);

            lineStart = nextLineStart;
            progress = nextLineStart;
        }

        return progress;
    }

    private void onLine(
        DirectBuffer buffer,
        int start,
        int end,
        LlmContentDecoderOutput output)
    {
        if (start == end)
        {
            if (dataFieldSeen)
            {
                output.flush(eventName, eventId, 0, eventIdLength);
                dataFieldSeen = false;
            }
            eventName = null;
        }
        else if (buffer.getByte(start) != COLON)
        {
            onField(buffer, start, end, output);
        }
    }

    private void onField(
        DirectBuffer buffer,
        int start,
        int end,
        LlmContentDecoderOutput output)
    {
        int colon = start;
        while (colon < end && buffer.getByte(colon) != COLON)
        {
            colon++;
        }

        int valueStart = colon < end ? colon + 1 : end;
        if (valueStart < end && buffer.getByte(valueStart) == SPACE)
        {
            valueStart++;
        }
        int valueLength = end - valueStart;

        String field = buffer.getStringWithoutLengthUtf8(start, colon - start);

        switch (field)
        {
        case DATA_FIELD:
            onEventData(buffer, valueStart, valueLength, output);
            break;
        case EVENT_FIELD:
            eventName = buffer.getStringWithoutLengthUtf8(valueStart, valueLength);
            break;
        case ID_FIELD:
            onEventId(buffer, valueStart, valueLength);
            break;
        default:
            break;
        }
    }

    private void onEventData(
        DirectBuffer buffer,
        int valueStart,
        int valueLength,
        LlmContentDecoderOutput output)
    {
        if (dataFieldSeen)
        {
            output.data(LINE_FEED, 0, LINE_FEED.capacity());
        }

        output.data(buffer, valueStart, valueLength);
        dataFieldSeen = true;
    }

    private void onEventId(
        DirectBuffer buffer,
        int valueStart,
        int valueLength)
    {
        if (!containsNul(buffer, valueStart, valueLength))
        {
            if (eventIdBytes.length < valueLength)
            {
                eventIdBytes = new byte[valueLength];
                eventId.wrap(eventIdBytes);
            }
            eventId.putBytes(0, buffer, valueStart, valueLength);
            eventIdLength = valueLength;
        }
    }

    private static boolean containsNul(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        boolean containsNul = false;
        for (int cursor = offset; cursor < offset + length && !containsNul; cursor++)
        {
            containsNul = buffer.getByte(cursor) == NUL;
        }
        return containsNul;
    }
}
