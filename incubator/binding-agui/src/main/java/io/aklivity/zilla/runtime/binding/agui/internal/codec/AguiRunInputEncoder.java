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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import java.util.Map;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;

public final class AguiRunInputEncoder
{
    public enum Status
    {
        PENDING,
        VALID,
        MISMATCHED,
        MALFORMED
    }

    private enum Identifier
    {
        THREAD_ID,
        RUN_ID,
        PARENT_RUN_ID
    }

    @FunctionalInterface
    private interface AguiRunInputDecoder
    {
        int decode();
    }

    private static final int IDENTIFIER_MAX = 256;
    private static final int WINDOW_MAX = 1024;

    private final AguiRunInputDecoder decodeStart = this::decodeStart;
    private final AguiRunInputDecoder decodeObjectStart = this::decodeObjectStart;
    private final AguiRunInputDecoder decodeMember = this::decodeMember;
    private final AguiRunInputDecoder decodeIdentifier = this::decodeIdentifier;
    private final AguiRunInputDecoder decodeEnd = this::decodeEnd;
    private final AguiRunInputDecoder decodeMismatched = this::decodeMismatched;
    private final AguiRunInputDecoder decodeMalformed = this::decodeMalformed;

    private final String threadId;
    private final String runId;
    private final String parentRunId;
    private final JsonParserEx parser =
        JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
    private final MutableDirectBufferEx windowBuffer = new UnsafeBufferEx(new byte[WINDOW_MAX]);

    private final StringBuilder identified = new StringBuilder();

    private AguiRunInputDecoder decoder = decodeStart;
    private Identifier identifier;
    private int carryLength;
    private int windowLimit;
    private int seenThreadId;
    private int seenRunId;
    private int seenParentRunId;

    public AguiRunInputEncoder(
        String threadId,
        String runId,
        String parentRunId)
    {
        this.threadId = threadId;
        this.runId = runId;
        this.parentRunId = parentRunId;
    }

    public Status encode(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        while (progress < limit && decoder != decodeMismatched && decoder != decodeMalformed)
        {
            final int copied = Math.min(WINDOW_MAX - carryLength, limit - progress);
            windowBuffer.putBytes(carryLength, buffer, progress, copied);
            progress += copied;

            decodeWindow(carryLength + copied);
        }

        return status();
    }

    public Status finish()
    {
        if (decoder == decodeEnd)
        {
            final boolean identified = seenThreadId == 1 && seenRunId == 1;
            final boolean parented = parentRunId == null || seenParentRunId == 1;

            decoder = identified && parented ? decodeEnd : decodeMismatched;
        }
        else if (decoder != decodeMismatched)
        {
            decoder = decodeMalformed;
        }

        return decoder == decodeEnd ? Status.VALID : status();
    }

    private Status status()
    {
        return decoder == decodeMismatched ? Status.MISMATCHED : decoder == decodeMalformed ? Status.MALFORMED : Status.PENDING;
    }

    private void decodeWindow(
        int limit)
    {
        windowLimit = limit;
        parser.wrap(windowBuffer, 0, limit, false);

        int position = 0;

        try
        {
            int previous;
            AguiRunInputDecoder previousDecoder;

            do
            {
                previous = position;
                previousDecoder = decoder;
                position = decoder.decode();
            }
            while (position != previous || decoder != previousDecoder);
        }
        catch (JsonException ex)
        {
            decoder = decodeMalformed;
        }

        carryLength = windowLimit - position;
        System.arraycopy(windowBuffer.byteArray(), position, windowBuffer.byteArray(), 0, carryLength);
    }

    private int position()
    {
        return windowLimit - parser.remaining();
    }

    private int decodeStart()
    {
        if (parser.hasNextEvent())
        {
            decoder = parser.nextEvent() == JsonEvent.START_DOCUMENT ? decodeObjectStart : decodeMalformed;
        }

        return position();
    }

    private int decodeObjectStart()
    {
        if (parser.hasNextEvent())
        {
            decoder = parser.nextEvent() == JsonEvent.START_OBJECT ? decodeMember : decodeMalformed;
        }

        return position();
    }

    private int decodeMember()
    {
        if (parser.hasNextEvent())
        {
            switch (parser.nextEvent())
            {
            case KEY_NAME:
                decodeKey();
                break;
            case END_OBJECT:
                decoder = decodeEnd;
                break;
            default:
                decoder = decodeMalformed;
                break;
            }
        }

        return position();
    }

    private void decodeKey()
    {
        if (parser.deferredBytes())
        {
            parser.consumed(0);
        }
        else
        {
            final CharSequence key = parser.getStringView();

            if ("threadId".contentEquals(key))
            {
                identifier = Identifier.THREAD_ID;
                decoder = decodeIdentifier;
            }
            else if ("runId".contentEquals(key))
            {
                identifier = Identifier.RUN_ID;
                decoder = decodeIdentifier;
            }
            else if ("parentRunId".contentEquals(key))
            {
                identifier = Identifier.PARENT_RUN_ID;
                decoder = decodeIdentifier;
            }
            else
            {
                parser.skipValue();
            }
        }
    }

    private int decodeIdentifier()
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();

            if (event == JsonEvent.VALUE_STRING)
            {
                final CharSequence fragment = parser.getStringView();
                final int length = fragment.length();
                final boolean deferred = parser.deferredBytes();

                identified.append(fragment);

                if (identified.length() > IDENTIFIER_MAX)
                {
                    decoder = decodeMismatched;
                }
                else if (deferred)
                {
                    parser.consumed(length);
                }
                else
                {
                    decodeIdentified(identified.toString());
                }
            }
            else if (event == JsonEvent.VALUE_NULL && identifier == Identifier.PARENT_RUN_ID)
            {
                decodeIdentified(null);
            }
            else
            {
                decoder = decodeMalformed;
            }
        }

        return position();
    }

    private void decodeIdentified(
        String value)
    {
        final boolean matched;

        switch (identifier)
        {
        case THREAD_ID:
            matched = threadId.equals(value);
            seenThreadId = 1;
            break;
        case RUN_ID:
            matched = runId.equals(value);
            seenRunId = 1;
            break;
        default:
            matched = parentRunId == null ? value == null : parentRunId.equals(value);
            seenParentRunId = value != null ? 1 : 0;
            break;
        }

        identified.setLength(0);
        decoder = matched ? decodeMember : decodeMismatched;
    }

    private int decodeEnd()
    {
        for (int index = position(); index < windowLimit; index++)
        {
            if (!isWhitespace(windowBuffer.getByte(index)))
            {
                decoder = decodeMalformed;
            }
        }

        return windowLimit;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }

    private int decodeMismatched()
    {
        return windowLimit;
    }

    private int decodeMalformed()
    {
        return windowLimit;
    }
}
