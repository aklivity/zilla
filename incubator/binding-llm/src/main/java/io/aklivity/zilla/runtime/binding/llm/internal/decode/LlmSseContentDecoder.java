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

import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentDecoder;
import io.aklivity.zilla.runtime.binding.llm.codec.LlmContentDecoderOutput;

/**
 * Decodes {@code text/event-stream} (SSE) framing: blank-line-delimited events carrying
 * {@code data:}, {@code event:} and {@code id:} fields, per the WHATWG SSE parsing algorithm.
 * <p>
 * Framing decode only; the decoded {@code data} bytes are the raw SSE payload with no
 * dialect-specific (e.g. OpenAI, Anthropic) interpretation applied.
 * <p>
 * A {@code data:} field's value streams to {@link LlmContentDecoderOutput#data} as bytes arrive,
 * without waiting for the value's own terminating line break -- so a single field's value may span
 * any number of {@link #decode} calls, of any total size. {@code event:}/{@code id:} field values
 * stay whole-line-buffered (they are always short in practice, and {@code id:} already grows its
 * stored buffer on demand).
 */
public final class LlmSseContentDecoder implements LlmContentDecoder
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

    private Line line = Line.NAME;
    private FieldKind fieldKind;
    private boolean spacePending;
    private boolean awaitingAvailable;
    private boolean dataValueFinalized;
    private final GrowableBuffer fieldName = new GrowableBuffer();
    private final GrowableBuffer fieldValue = new GrowableBuffer();

    @Override
    public boolean streaming()
    {
        return true;
    }

    @Override
    public int decode(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output)
    {
        int progress = offset;

        int previousProgress = -1;
        Line previousLine = null;
        while (progress < limit && (previousProgress != progress || previousLine != line))
        {
            previousProgress = progress;
            previousLine = line;
            switch (line)
            {
            case NAME:
                progress = decodeName(buffer, progress, limit, output);
                break;
            case COMMENT:
                progress = decodeComment(buffer, progress, limit);
                break;
            case DATA_VALUE:
                progress = decodeDataValue(buffer, progress, limit, output);
                break;
            case OTHER_VALUE:
                progress = decodeOtherValue(buffer, progress, limit, output);
                break;
            default:
                break;
            }
        }

        return progress;
    }

    private int decodeName(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output)
    {
        int pos = offset;

        if (fieldName.length() == 0 && pos < limit && buffer.getByte(pos) == COLON)
        {
            line = Line.COMMENT;
            return pos;
        }

        while (pos < limit)
        {
            byte b = buffer.getByte(pos);
            if (b == COLON)
            {
                fieldKind = fieldKindOf(fieldName);
                fieldName.clear();
                spacePending = true;
                if (fieldKind == FieldKind.DATA)
                {
                    beginDataField(output);
                }
                else
                {
                    line = Line.OTHER_VALUE;
                }
                return pos + 1;
            }
            else if (b == LF || b == CR)
            {
                int nextLineStart;
                if (b == CR)
                {
                    if (pos + 1 >= limit)
                    {
                        return pos;
                    }
                    nextLineStart = buffer.getByte(pos + 1) == LF ? pos + 2 : pos + 1;
                }
                else
                {
                    nextLineStart = pos + 1;
                }

                onLineWithoutColon(output);
                fieldName.clear();
                return nextLineStart;
            }
            else
            {
                fieldName.append(buffer, pos, 1);
                pos++;
            }
        }

        return pos;
    }

    private int decodeComment(
        DirectBuffer buffer,
        int offset,
        int limit)
    {
        int pos = offset;

        while (pos < limit)
        {
            byte b = buffer.getByte(pos);
            if (b == LF)
            {
                line = Line.NAME;
                return pos + 1;
            }
            else if (b == CR)
            {
                if (pos + 1 >= limit)
                {
                    return pos;
                }
                line = Line.NAME;
                return buffer.getByte(pos + 1) == LF ? pos + 2 : pos + 1;
            }
            pos++;
        }

        return pos;
    }

    private int decodeDataValue(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output)
    {
        int pos = offset;

        if (awaitingAvailable)
        {
            if (!output.available())
            {
                return pos;
            }
            awaitingAvailable = false;
        }

        if (spacePending)
        {
            if (pos >= limit)
            {
                return pos;
            }
            spacePending = false;
            if (buffer.getByte(pos) == SPACE)
            {
                pos++;
            }
        }

        int valueStart = pos;
        while (pos < limit && buffer.getByte(pos) != LF && buffer.getByte(pos) != CR)
        {
            pos++;
        }

        int result;
        if (pos == limit || buffer.getByte(pos) == CR && pos + 1 >= limit)
        {
            if (pos > valueStart)
            {
                output.data(buffer, valueStart, pos - valueStart, false);
                if (!output.available())
                {
                    awaitingAvailable = true;
                }
            }
            result = pos;
        }
        else
        {
            final int nextLineStart = buffer.getByte(pos) == CR
                ? (buffer.getByte(pos + 1) == LF ? pos + 2 : pos + 1)
                : pos + 1;

            if (!dataValueFinalized)
            {
                output.data(buffer, valueStart, pos - valueStart, true);
                dataValueFinalized = true;
            }

            if (!output.available())
            {
                awaitingAvailable = true;
                result = pos;
            }
            else
            {
                dataValueFinalized = false;
                line = Line.NAME;
                result = nextLineStart;
            }
        }
        return result;
    }

    private int decodeOtherValue(
        DirectBuffer buffer,
        int offset,
        int limit,
        LlmContentDecoderOutput output)
    {
        int pos = offset;

        if (spacePending)
        {
            if (pos >= limit)
            {
                return pos;
            }
            spacePending = false;
            if (buffer.getByte(pos) == SPACE)
            {
                pos++;
            }
        }

        int valueStart = pos;
        while (pos < limit && buffer.getByte(pos) != LF && buffer.getByte(pos) != CR)
        {
            pos++;
        }

        if (pos > valueStart)
        {
            fieldValue.append(buffer, valueStart, pos - valueStart);
        }

        int result;
        if (pos == limit)
        {
            result = pos;
        }
        else
        {
            int nextLineStart;
            if (buffer.getByte(pos) == CR)
            {
                if (pos + 1 >= limit)
                {
                    return pos;
                }
                nextLineStart = buffer.getByte(pos + 1) == LF ? pos + 2 : pos + 1;
            }
            else
            {
                nextLineStart = pos + 1;
            }

            dispatchOtherValue(output);
            fieldValue.clear();
            line = Line.NAME;
            result = nextLineStart;
        }
        return result;
    }

    private void beginDataField(
        LlmContentDecoderOutput output)
    {
        if (dataFieldSeen)
        {
            output.data(LINE_FEED, 0, LINE_FEED.capacity(), false);
        }
        dataFieldSeen = true;
        line = Line.DATA_VALUE;
        dataValueFinalized = false;
    }

    private void onLineWithoutColon(
        LlmContentDecoderOutput output)
    {
        if (fieldName.length() == 0)
        {
            onBlankLine(output);
        }
        else
        {
            switch (fieldKindOf(fieldName))
            {
            case DATA:
                beginDataField(output);
                output.data(LINE_FEED, 0, 0, true);
                line = Line.NAME;
                break;
            case EVENT:
                eventName = "";
                output.event(eventName);
                break;
            case ID:
                onEventId(fieldValue.buffer(), 0, 0);
                break;
            default:
                break;
            }
        }
    }

    private void onBlankLine(
        LlmContentDecoderOutput output)
    {
        if (dataFieldSeen)
        {
            output.flush(eventName, eventId, 0, eventIdLength);
            dataFieldSeen = false;
        }
        eventName = null;
    }

    private void dispatchOtherValue(
        LlmContentDecoderOutput output)
    {
        switch (fieldKind)
        {
        case EVENT:
            eventName = fieldValue.buffer().getStringWithoutLengthUtf8(0, fieldValue.length());
            output.event(eventName);
            break;
        case ID:
            onEventId(fieldValue.buffer(), 0, fieldValue.length());
            break;
        default:
            break;
        }
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

    private static FieldKind fieldKindOf(
        GrowableBuffer name)
    {
        FieldKind kind;
        if (matches(name, DATA_FIELD))
        {
            kind = FieldKind.DATA;
        }
        else if (matches(name, EVENT_FIELD))
        {
            kind = FieldKind.EVENT;
        }
        else if (matches(name, ID_FIELD))
        {
            kind = FieldKind.ID;
        }
        else
        {
            kind = FieldKind.OTHER;
        }
        return kind;
    }

    private static boolean matches(
        GrowableBuffer name,
        String literal)
    {
        boolean matches = name.length() == literal.length();
        for (int i = 0; matches && i < literal.length(); i++)
        {
            matches = name.buffer().getByte(i) == (byte) literal.charAt(i);
        }
        return matches;
    }

    private enum Line
    {
        NAME,
        COMMENT,
        DATA_VALUE,
        OTHER_VALUE
    }

    private enum FieldKind
    {
        DATA,
        EVENT,
        ID,
        OTHER
    }

    private static final class GrowableBuffer
    {
        private byte[] bytes = new byte[0];
        private final MutableDirectBuffer view = new UnsafeBuffer(bytes);
        private int length;

        void clear()
        {
            length = 0;
        }

        void append(
            DirectBuffer source,
            int offset,
            int appendLength)
        {
            ensureCapacity(length + appendLength);
            view.putBytes(length, source, offset, appendLength);
            length += appendLength;
        }

        int length()
        {
            return length;
        }

        DirectBuffer buffer()
        {
            return view;
        }

        private void ensureCapacity(
            int required)
        {
            if (bytes.length < required)
            {
                byte[] grown = new byte[required];
                System.arraycopy(bytes, 0, grown, 0, length);
                bytes = grown;
                view.wrap(bytes);
            }
        }
    }
}
