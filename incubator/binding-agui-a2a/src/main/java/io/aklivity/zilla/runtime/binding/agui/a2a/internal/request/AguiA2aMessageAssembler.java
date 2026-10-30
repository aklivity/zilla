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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aText.matches;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Kind;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;

public final class AguiA2aMessageAssembler
{
    public enum ResumeStatus
    {
        RESOLVED,
        CANCELLED
    }

    private enum PartType
    {
        NONE,
        TEXT,
        MEDIA
    }

    private enum SourceType
    {
        NONE,
        DATA,
        URL
    }

    private static final int ABSENT = -1;

    private static final DirectBufferEx MESSAGE_OPEN = literal("{\"message\":{\"messageId\":");
    private static final DirectBufferEx ROLE = literal(",\"role\":\"ROLE_USER\"");
    private static final DirectBufferEx CONTEXT_ID = literal(",\"contextId\":");
    private static final DirectBufferEx TASK_ID = literal(",\"taskId\":");
    private static final DirectBufferEx PARTS_OPEN = literal(",\"parts\":[");
    private static final DirectBufferEx PARTS_CLOSE = literal("]");
    private static final DirectBufferEx METADATA_OPEN = literal(",\"metadata\":{");
    private static final DirectBufferEx METADATA_CLOSE = literal("}");
    private static final DirectBufferEx MESSAGE_CLOSE = literal("},\"configuration\":{\"historyLength\":0," +
        "\"acceptedOutputModes\":[\"text/plain\"]}}");
    private static final DirectBufferEx COMMA = literal(",");
    private static final DirectBufferEx TEXT_OPEN = literal("{\"text\":");
    private static final DirectBufferEx RAW_OPEN = literal("{\"raw\":");
    private static final DirectBufferEx URL_OPEN = literal("{\"url\":");
    private static final DirectBufferEx MEDIA_TYPE = literal(",\"mediaType\":");
    private static final DirectBufferEx PART_METADATA = literal(",\"metadata\":");
    private static final DirectBufferEx OBJECT_CLOSE = literal("}");
    private static final DirectBufferEx DATA_OPEN = literal("{\"data\":{");
    private static final DirectBufferEx DATA_STATE = literal("\"state\":");
    private static final DirectBufferEx DATA_CONTEXT = literal("\"context\":");
    private static final DirectBufferEx DATA_CLOSE = literal("},\"mediaType\":\"application/json\"," +
        "\"metadata\":{\"agui\":{\"part\":\"input\"}}}");
    private static final DirectBufferEx DATA_PAYLOAD_OPEN = literal("{\"data\":");
    private static final DirectBufferEx DATA_EMPTY = literal("{\"data\":{}}");
    private static final DirectBufferEx FORWARDED_PROPS = literal("\"forwardedProps\":");
    private static final DirectBufferEx AGUI_RESUME_OPEN = literal("\"agui\":{\"resume\":");
    private static final DirectBufferEx AGUI_RESUME_CLOSE = literal("}");
    private static final DirectBufferEx QUOTE = literal("\"");

    private final JsonParserEx parser =
        JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
    private final UnsafeBufferEx escapeBuffer = new UnsafeBufferEx(new byte[0]);
    private final int[] range = new int[2];

    private byte[] escapeBytes = new byte[256];
    private AguiA2aRetained retained;
    private DirectBufferEx slot;
    private int entryLimit;
    private String runId;
    private String contextId;
    private String taskId;
    private String interruptId;
    private boolean acceptsJson;
    private AguiA2aBodySink sink;
    private int partCount;
    private int resumePayloadStart;
    private int resumePayloadEnd;
    private int resumeMetadataStart;
    private int resumeMetadataEnd;

    public AguiA2aMessageAssembler wrap(
        AguiA2aRetained retained,
        String runId,
        String contextId,
        String taskId,
        String interruptId,
        boolean acceptsJson)
    {
        this.retained = retained;
        this.slot = retained.buffer();
        this.runId = runId;
        this.contextId = contextId;
        this.taskId = taskId;
        this.interruptId = interruptId;
        this.acceptsJson = acceptsJson;
        return this;
    }

    public int contentLength()
    {
        final int[] length = new int[1];
        emit((buffer, offset, count) -> length[0] += count);
        return length[0];
    }

    public void emit(
        AguiA2aBodySink sink)
    {
        this.sink = sink;
        this.partCount = 0;

        write(MESSAGE_OPEN);
        emitMessageId();
        write(ROLE);

        if (contextId != null)
        {
            write(CONTEXT_ID);
            writeString(contextId);
        }

        if (taskId != null)
        {
            write(TASK_ID);
            writeString(taskId);
        }

        write(PARTS_OPEN);

        if (interruptId != null)
        {
            emitResumeParts();
        }
        else
        {
            emitMessageParts();
            emitDataPart();
        }

        write(PARTS_CLOSE);
        emitMetadata();
        write(MESSAGE_CLOSE);
    }

    public int resumeCount(
        AguiA2aRetained retained)
    {
        int count = 0;

        if (openEntry(retained, Kind.RESUME) && parser.nextEvent() == JsonEvent.START_ARRAY)
        {
            while (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                consumeRest(1);
                count++;
            }
        }

        return count;
    }

    public String resumeInterruptId(
        AguiA2aRetained retained,
        int index)
    {
        String interruptId = null;

        if (openEntry(retained, Kind.RESUME) && parser.nextEvent() == JsonEvent.START_ARRAY)
        {
            int current = 0;
            while (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                final String id = resumeMember("interruptId");
                interruptId = current == index ? id : interruptId;
                current++;
            }
        }

        return interruptId;
    }

    public ResumeStatus resumeStatus(
        AguiA2aRetained retained,
        int index)
    {
        ResumeStatus status = ResumeStatus.RESOLVED;

        if (openEntry(retained, Kind.RESUME) && parser.nextEvent() == JsonEvent.START_ARRAY)
        {
            int current = 0;
            while (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                final String value = resumeMember("status");
                status = current == index && "cancelled".equals(value) ? ResumeStatus.CANCELLED : status;
                current++;
            }
        }

        return status;
    }

    private String resumeMember(
        String name)
    {
        String value = null;

        JsonEvent event = parser.nextEvent();
        while (event != JsonEvent.END_OBJECT)
        {
            if (matches(parser.getStringView(), name))
            {
                value = parser.nextEvent() == JsonEvent.VALUE_STRING ? parser.getString() : null;
            }
            else
            {
                consumeValue();
            }

            event = parser.nextEvent();
        }

        return value;
    }

    private void emitMessageId()
    {
        final int last = lastMessage();
        boolean identified = false;

        if (interruptId == null && last != ABSENT)
        {
            openIndex(last);
        }

        if (interruptId == null && last != ABSENT && parser.nextEvent() == JsonEvent.START_OBJECT)
        {
            JsonEvent event = parser.nextEvent();
            while (!identified && event != JsonEvent.END_OBJECT)
            {
                if (matches(parser.getStringView(), "id"))
                {
                    final int start = position();
                    final JsonEvent value = parser.nextEvent();
                    identified = value == JsonEvent.VALUE_STRING;
                    if (identified)
                    {
                        writeRaw(start, position());
                    }
                    else
                    {
                        consumeRest(value == JsonEvent.START_OBJECT || value == JsonEvent.START_ARRAY ? 1 : 0);
                    }
                }
                else
                {
                    consumeValue();
                }

                event = identified ? event : parser.nextEvent();
            }
        }

        if (!identified)
        {
            writeString(runId);
        }
    }

    private int lastMessage()
    {
        int last = ABSENT;
        int index = retained.indexOf(Kind.MESSAGE);
        while (index != ABSENT)
        {
            last = index;
            index = retained.indexOf(Kind.MESSAGE, index + 1);
        }

        return last;
    }

    private void emitMessageParts()
    {
        int index = retained.indexOf(Kind.MESSAGE);
        while (index != ABSENT)
        {
            openIndex(index);

            if (parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                JsonEvent event = parser.nextEvent();
                while (event != JsonEvent.END_OBJECT)
                {
                    if (matches(parser.getStringView(), "content"))
                    {
                        emitContent();
                    }
                    else
                    {
                        consumeValue();
                    }

                    event = parser.nextEvent();
                }
            }

            index = retained.indexOf(Kind.MESSAGE, index + 1);
        }
    }

    private void emitContent()
    {
        final int start = position();

        switch (parser.nextEvent())
        {
        case VALUE_STRING:
            separate();
            write(TEXT_OPEN);
            writeRaw(start, position());
            write(OBJECT_CLOSE);
            break;
        case START_ARRAY:
            JsonEvent event = parser.nextEvent();
            while (event != JsonEvent.END_ARRAY)
            {
                if (event == JsonEvent.START_OBJECT)
                {
                    emitPart();
                }
                else
                {
                    consumeRest(event == JsonEvent.START_ARRAY ? 1 : 0);
                }

                event = parser.nextEvent();
            }
            break;
        default:
            break;
        }
    }

    private void emitPart()
    {
        PartType type = PartType.NONE;
        SourceType sourceType = SourceType.NONE;
        int textStart = ABSENT;
        int textEnd = ABSENT;
        int valueStart = ABSENT;
        int valueEnd = ABSENT;
        int mimeStart = ABSENT;
        int mimeEnd = ABSENT;
        int metadataStart = ABSENT;
        int metadataEnd = ABSENT;

        JsonEvent event = parser.nextEvent();
        while (event != JsonEvent.END_OBJECT)
        {
            final CharSequence name = parser.getStringView();

            if (matches(name, "type"))
            {
                type = parser.nextEvent() == JsonEvent.VALUE_STRING ? partType(parser.getStringView()) : PartType.NONE;
            }
            else if (matches(name, "text"))
            {
                rawValue();
                textStart = range[0];
                textEnd = range[1];
            }
            else if (matches(name, "metadata"))
            {
                rawValue();
                metadataStart = range[0];
                metadataEnd = range[1];
            }
            else if (matches(name, "source"))
            {
                if (parser.nextEvent() == JsonEvent.START_OBJECT)
                {
                    JsonEvent member = parser.nextEvent();
                    while (member != JsonEvent.END_OBJECT)
                    {
                        final CharSequence key = parser.getStringView();

                        if (matches(key, "type"))
                        {
                            sourceType = parser.nextEvent() == JsonEvent.VALUE_STRING ? sourceType(parser.getStringView()) :
                                SourceType.NONE;
                        }
                        else if (matches(key, "value"))
                        {
                            rawValue();
                            valueStart = range[0];
                            valueEnd = range[1];
                        }
                        else if (matches(key, "mimeType"))
                        {
                            rawValue();
                            mimeStart = range[0];
                            mimeEnd = range[1];
                        }
                        else
                        {
                            consumeValue();
                        }

                        member = parser.nextEvent();
                    }
                }
            }
            else
            {
                consumeValue();
            }

            event = parser.nextEvent();
        }

        if (type == PartType.TEXT && textStart != ABSENT)
        {
            separate();
            write(TEXT_OPEN);
            writeRaw(textStart, textEnd);
            writeMetadata(metadataStart, metadataEnd);
            write(OBJECT_CLOSE);
        }
        else if (type == PartType.MEDIA && valueStart != ABSENT && sourceType != SourceType.NONE)
        {
            separate();
            write(sourceType == SourceType.DATA ? RAW_OPEN : URL_OPEN);
            writeRaw(valueStart, valueEnd);

            if (mimeStart != ABSENT)
            {
                write(MEDIA_TYPE);
                writeRaw(mimeStart, mimeEnd);
            }

            writeMetadata(metadataStart, metadataEnd);
            write(OBJECT_CLOSE);
        }
    }

    private void writeMetadata(
        int start,
        int end)
    {
        if (start != ABSENT)
        {
            write(PART_METADATA);
            writeRaw(start, end);
        }
    }

    private void emitDataPart()
    {
        final int state = retained.indexOf(Kind.STATE);
        final int context = retained.indexOf(Kind.CONTEXT);
        final boolean stated = state != ABSENT && !empty(state);
        final boolean contexted = context != ABSENT && !empty(context);

        if (acceptsJson && (stated || contexted))
        {
            separate();
            write(DATA_OPEN);

            if (stated)
            {
                write(DATA_STATE);
                writeEntry(state);
            }

            if (contexted)
            {
                if (stated)
                {
                    write(COMMA);
                }

                write(DATA_CONTEXT);
                writeEntry(context);
            }

            write(DATA_CLOSE);
        }
    }

    private void emitResumeParts()
    {
        resumePayloadStart = ABSENT;
        resumeMetadataStart = ABSENT;

        boolean matched = false;
        if (openEntry(retained, Kind.RESUME) && parser.nextEvent() == JsonEvent.START_ARRAY)
        {
            while (!matched && parser.nextEvent() == JsonEvent.START_OBJECT)
            {
                matched = resumeEntry();
            }
        }

        separate();

        if (!matched || resumePayloadStart == ABSENT || isNull(resumePayloadStart, resumePayloadEnd))
        {
            write(DATA_EMPTY);
        }
        else if (slot.getByte(resumePayloadStart) == '"')
        {
            write(TEXT_OPEN);
            writeRaw(resumePayloadStart, resumePayloadEnd);
            write(OBJECT_CLOSE);
        }
        else
        {
            write(DATA_PAYLOAD_OPEN);
            writeRaw(resumePayloadStart, resumePayloadEnd);
            write(OBJECT_CLOSE);
        }
    }

    private boolean resumeEntry()
    {
        boolean matched = false;
        int payloadStart = ABSENT;
        int payloadEnd = ABSENT;
        int metadataStart = ABSENT;
        int metadataEnd = ABSENT;

        JsonEvent event = parser.nextEvent();
        while (event != JsonEvent.END_OBJECT)
        {
            final CharSequence name = parser.getStringView();

            if (matches(name, "interruptId"))
            {
                matched = parser.nextEvent() == JsonEvent.VALUE_STRING && matches(parser.getStringView(), interruptId);
            }
            else if (matches(name, "payload"))
            {
                rawValue();
                payloadStart = range[0];
                payloadEnd = range[1];
            }
            else if (matches(name, "metadata"))
            {
                rawValue();
                metadataStart = range[0];
                metadataEnd = range[1];
            }
            else
            {
                consumeValue();
            }

            event = parser.nextEvent();
        }

        if (matched)
        {
            resumePayloadStart = payloadStart;
            resumePayloadEnd = payloadEnd;
            resumeMetadataStart = metadataStart;
            resumeMetadataEnd = metadataEnd;
        }

        return matched;
    }

    private void emitMetadata()
    {
        final int props = retained.indexOf(Kind.FORWARDED_PROPS);
        int memberStart = ABSENT;
        int memberEnd = ABSENT;
        boolean wrapped = false;

        if (props != ABSENT)
        {
            final int start = retained.offset(props);
            final int end = start + retained.length(props);
            final byte first = slot.getByte(start);

            if (first == '{')
            {
                memberStart = trimStart(start + 1, end - 1);
                memberEnd = trimEnd(memberStart, end - 1);
            }
            else if (!isNull(start, end))
            {
                memberStart = start;
                memberEnd = end;
                wrapped = true;
            }
        }

        final boolean members = memberStart != ABSENT && memberStart < memberEnd;
        final boolean resumed = interruptId != null && resumeMetadataStart != ABSENT &&
            !isNull(resumeMetadataStart, resumeMetadataEnd);

        if (members || resumed)
        {
            write(METADATA_OPEN);

            if (members)
            {
                if (wrapped)
                {
                    write(FORWARDED_PROPS);
                }

                writeRaw(memberStart, memberEnd);
            }

            if (resumed)
            {
                if (members)
                {
                    write(COMMA);
                }

                write(AGUI_RESUME_OPEN);
                writeRaw(resumeMetadataStart, resumeMetadataEnd);
                write(AGUI_RESUME_CLOSE);
            }

            write(METADATA_CLOSE);
        }
    }

    private boolean empty(
        int index)
    {
        final int start = retained.offset(index);
        final int end = start + retained.length(index);
        final byte first = slot.getByte(start);

        boolean empty = isNull(start, end);

        if (first == '{' || first == '[')
        {
            empty = trimStart(start + 1, end - 1) == end - 1;
        }

        return empty;
    }

    private boolean isNull(
        int start,
        int end)
    {
        return end - start == 4 &&
            slot.getByte(start) == 'n' && slot.getByte(start + 1) == 'u' &&
            slot.getByte(start + 2) == 'l' && slot.getByte(start + 3) == 'l';
    }

    private int trimStart(
        int start,
        int end)
    {
        int index = start;
        while (index < end && isWhitespace(slot.getByte(index)))
        {
            index++;
        }

        return index;
    }

    private int trimEnd(
        int start,
        int end)
    {
        int index = end;
        while (index > start && isWhitespace(slot.getByte(index - 1)))
        {
            index--;
        }

        return index;
    }

    private boolean openEntry(
        AguiA2aRetained retained,
        Kind kind)
    {
        this.retained = retained;
        this.slot = retained.buffer();

        final int index = retained.indexOf(kind);

        if (index != ABSENT)
        {
            openIndex(index);
        }

        return index != ABSENT;
    }

    private void openIndex(
        int index)
    {
        final int offset = retained.offset(index);
        entryLimit = offset + retained.length(index);
        parser.reset();
        parser.wrap(slot, offset, entryLimit, true);
        parser.nextEvent();
    }

    private int position()
    {
        return entryLimit - parser.remaining();
    }

    private void rawValue()
    {
        range[0] = position();
        consumeValue();
        range[1] = trimEnd(range[0], position());
        range[0] = trimStart(range[0], range[1]);
    }

    private void consumeValue()
    {
        consumeRest(switch (parser.nextEvent())
        {
        case START_OBJECT, START_ARRAY -> 1;
        default -> 0;
        });
    }

    private void consumeRest(
        int depth)
    {
        int remaining = depth;
        while (remaining > 0)
        {
            switch (parser.nextEvent())
            {
            case START_OBJECT:
            case START_ARRAY:
                remaining++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                remaining--;
                break;
            default:
                break;
            }
        }
    }

    private void separate()
    {
        if (partCount++ > 0)
        {
            write(COMMA);
        }
    }

    private void write(
        DirectBufferEx literal)
    {
        sink.write(literal, 0, literal.capacity());
    }

    private void writeRaw(
        int start,
        int end)
    {
        sink.write(slot, start, end - start);
    }

    private void writeEntry(
        int index)
    {
        final int offset = retained.offset(index);
        sink.write(slot, offset, retained.length(index));
    }

    private void writeString(
        String value)
    {
        final int worst = value.length() * 6 + 2;
        if (escapeBytes.length < worst)
        {
            escapeBytes = new byte[worst];
        }

        int length = 0;
        escapeBytes[length++] = '"';

        int index = 0;
        while (index < value.length())
        {
            final int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);

            if (codePoint == '"' || codePoint == '\\')
            {
                escapeBytes[length++] = '\\';
                escapeBytes[length++] = (byte) codePoint;
            }
            else if (codePoint < 0x20)
            {
                length = escapeControl(codePoint, length);
            }
            else if (codePoint < 0x80)
            {
                escapeBytes[length++] = (byte) codePoint;
            }
            else
            {
                final byte[] encoded = new String(Character.toChars(codePoint)).getBytes(UTF_8);
                System.arraycopy(encoded, 0, escapeBytes, length, encoded.length);
                length += encoded.length;
            }
        }

        escapeBytes[length++] = '"';
        escapeBuffer.wrap(escapeBytes, 0, length);
        sink.write(escapeBuffer, 0, length);
    }

    private int escapeControl(
        int codePoint,
        int offset)
    {
        int length = offset;
        escapeBytes[length++] = '\\';

        switch (codePoint)
        {
        case '\b':
            escapeBytes[length++] = 'b';
            break;
        case '\f':
            escapeBytes[length++] = 'f';
            break;
        case '\n':
            escapeBytes[length++] = 'n';
            break;
        case '\r':
            escapeBytes[length++] = 'r';
            break;
        case '\t':
            escapeBytes[length++] = 't';
            break;
        default:
            escapeBytes[length++] = 'u';
            escapeBytes[length++] = '0';
            escapeBytes[length++] = '0';
            escapeBytes[length++] = (byte) Character.forDigit(codePoint >> 4, 16);
            escapeBytes[length++] = (byte) Character.forDigit(codePoint & 0xf, 16);
            break;
        }

        return length;
    }

    private static PartType partType(
        CharSequence view)
    {
        return matches(view, "text") ? PartType.TEXT :
            matches(view, "image") || matches(view, "audio") || matches(view, "video") || matches(view, "document") ?
                PartType.MEDIA : PartType.NONE;
    }

    private static SourceType sourceType(
        CharSequence view)
    {
        return matches(view, "data") ? SourceType.DATA : matches(view, "url") ? SourceType.URL : SourceType.NONE;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }

    private static DirectBufferEx literal(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
