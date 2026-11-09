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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;

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

    @FunctionalInterface
    private interface Walk
    {
        void walk();
    }

    private static final int ABSENT = -1;

    private static final int RANGE_TEXT = 0;
    private static final int RANGE_METADATA = 1;
    private static final int RANGE_VALUE = 2;
    private static final int RANGE_MIME_TYPE = 3;
    private static final int RANGE_PAYLOAD = 4;
    private static final int RANGE_RESUME_METADATA = 5;
    private static final int RANGE_ID = 6;
    private static final int RANGE_FOUND_PAYLOAD = 7;
    private static final int RANGE_FOUND_METADATA = 8;
    private static final int RANGE_COUNT = 9;

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

    private final Walk walkDone = this::walkDone;
    private final Walk walkSkip = this::walkSkip;
    private final Walk walkRaw = this::walkRaw;
    private final Walk walkIdStart = this::walkIdStart;
    private final Walk walkIdMember = this::walkIdMember;
    private final Walk walkMessage = this::walkMessage;
    private final Walk walkMessageMember = this::walkMessageMember;
    private final Walk walkContent = this::walkContent;
    private final Walk walkParts = this::walkParts;
    private final Walk walkPart = this::walkPart;
    private final Walk walkPartType = this::walkPartType;
    private final Walk walkSourceStart = this::walkSourceStart;
    private final Walk walkSource = this::walkSource;
    private final Walk walkSourceType = this::walkSourceType;
    private final Walk walkResumeStart = this::walkResumeStart;
    private final Walk walkResumeEntries = this::walkResumeEntries;
    private final Walk walkResumeEntry = this::walkResumeEntry;
    private final Walk walkResumeId = this::walkResumeId;
    private final Walk walkResumeStatus = this::walkResumeStatus;

    private final JsonParserEx parser =
        JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
    private final UnsafeBufferEx escapeBuffer = new UnsafeBufferEx(new byte[0]);
    private final int[] rangeStart = new int[RANGE_COUNT];
    private final int[] rangeEnd = new int[RANGE_COUNT];
    private final JsonEvent[] rangeEvent = new JsonEvent[RANGE_COUNT];

    private final AguiA2aBodySink gather = this::gather;
    private final UnsafeBufferEx scratch = new UnsafeBufferEx(new byte[0]);

    private DirectBufferEx[] gatherBuffers = new DirectBufferEx[16];
    private int[] gatherOffsets = new int[16];
    private int[] gatherLengths = new int[16];
    private byte[] scratchBytes = new byte[256];
    private int gatherCount;
    private int gatherIndex;
    private int gatherProgress;
    private int gatherLength;
    private int scratchLength;
    private byte[] escapeBytes = new byte[256];
    private AguiA2aRetained retained;
    private DirectBufferEx slot;
    private int entryLimit;
    private String runId;
    private String contextId;
    private String taskId;
    private String interruptId;
    private boolean acceptsJson;
    private Predicate<String> acceptedMediaType = mediaType -> true;
    private String unacceptedMediaType;
    private AguiA2aBodySink sink;
    private Walk walker = walkDone;
    private int partCount;
    private int messageIndex;
    private int contentStart;
    private PartType partType;
    private SourceType sourceType;
    private int rawRange;
    private int rawDepth;
    private int rawStart;
    private JsonEvent rawFirst;
    private Walk rawReturn;
    private int skipDepth;
    private Walk skipReturn;
    private int resumeIndex;
    private String resumeTarget;
    private int resumeCount;
    private int resumeFound;
    private String resumeFoundId;
    private String resumeFoundStatus;
    private String entryId;
    private String entryStatus;

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

    public AguiA2aMessageAssembler accepting(
        Predicate<String> acceptedMediaType)
    {
        this.acceptedMediaType = acceptedMediaType;
        return this;
    }

    public String unacceptedMediaType()
    {
        return unacceptedMediaType;
    }

    public int contentLength()
    {
        unacceptedMediaType = null;

        gatherCount = 0;
        gatherIndex = 0;
        gatherProgress = 0;
        gatherLength = 0;
        scratchLength = 0;

        walk(gather);

        return gatherLength;
    }

    public void emit(
        AguiA2aBodySink sink)
    {
        contentLength();
        emit(sink, Integer.MAX_VALUE);
    }

    public int emit(
        AguiA2aBodySink sink,
        int limit)
    {
        int emitted = 0;

        while (gatherIndex < gatherCount && emitted < limit)
        {
            final int length = Math.min(gatherLengths[gatherIndex] - gatherProgress, limit - emitted);
            final DirectBufferEx buffer = gatherBuffers[gatherIndex] != null ? gatherBuffers[gatherIndex] : scratch;

            sink.write(buffer, gatherOffsets[gatherIndex] + gatherProgress, length);

            emitted += length;
            gatherProgress += length;

            if (gatherProgress == gatherLengths[gatherIndex])
            {
                gatherIndex++;
                gatherProgress = 0;
            }
        }

        return emitted;
    }

    public boolean emitted()
    {
        return gatherIndex == gatherCount;
    }

    private void walk(
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
        walkResume(retained, ABSENT, null);
        return resumeCount;
    }

    public String resumeInterruptId(
        AguiA2aRetained retained,
        int index)
    {
        walkResume(retained, index, null);
        return resumeFoundId;
    }

    public ResumeStatus resumeStatus(
        AguiA2aRetained retained,
        int index)
    {
        walkResume(retained, index, null);
        return "cancelled".equals(resumeFoundStatus) ? ResumeStatus.CANCELLED : ResumeStatus.RESOLVED;
    }

    private void walkResume(
        AguiA2aRetained retained,
        int index,
        String target)
    {
        this.retained = retained;
        this.slot = retained.buffer();
        this.resumeIndex = index;
        this.resumeTarget = target;
        this.resumeCount = 0;
        this.resumeFound = ABSENT;
        this.resumeFoundId = null;
        this.resumeFoundStatus = null;
        resetRange(RANGE_FOUND_PAYLOAD);
        resetRange(RANGE_FOUND_METADATA);

        final int entry = retained.indexOf(Kind.RESUME);
        if (entry != ABSENT)
        {
            open(entry);
            pump(walkResumeStart);
        }
    }

    private void emitMessageId()
    {
        final int last = interruptId == null ? retained.lastIndexOf(Kind.MESSAGE) : ABSENT;

        resetRange(RANGE_ID);
        if (last != ABSENT)
        {
            open(last);
            pump(walkIdStart);
        }

        if (rangeEvent[RANGE_ID] == JsonEvent.VALUE_STRING)
        {
            writeRange(RANGE_ID);
        }
        else
        {
            writeString(runId);
        }
    }

    private void emitMessageParts()
    {
        messageIndex = retained.indexOf(Kind.MESSAGE);

        if (messageIndex != ABSENT)
        {
            open(messageIndex);
            pump(walkMessage);
        }
    }

    private void emitDataPart()
    {
        final int state = retained.indexOf(Kind.STATE);
        final int context = retained.indexOf(Kind.CONTEXT);

        if (acceptsJson && (state != ABSENT || context != ABSENT))
        {
            separate();
            write(DATA_OPEN);

            if (state != ABSENT)
            {
                write(DATA_STATE);
                writeEntry(state);
            }

            if (context != ABSENT)
            {
                if (state != ABSENT)
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
        walkResume(retained, ABSENT, interruptId);

        separate();

        if (resumeFound == ABSENT || rangeStart[RANGE_FOUND_PAYLOAD] == ABSENT ||
            rangeEvent[RANGE_FOUND_PAYLOAD] == JsonEvent.VALUE_NULL)
        {
            write(DATA_EMPTY);
        }
        else if (rangeEvent[RANGE_FOUND_PAYLOAD] == JsonEvent.VALUE_STRING)
        {
            write(TEXT_OPEN);
            writeRange(RANGE_FOUND_PAYLOAD);
            write(OBJECT_CLOSE);
        }
        else
        {
            write(DATA_PAYLOAD_OPEN);
            writeRange(RANGE_FOUND_PAYLOAD);
            write(OBJECT_CLOSE);
        }
    }

    private void emitMetadata()
    {
        final int members = retained.indexOf(Kind.FORWARDED_PROPS);
        final int value = retained.indexOf(Kind.FORWARDED_VALUE);
        final boolean resumed = interruptId != null && resumeFound != ABSENT &&
            rangeStart[RANGE_FOUND_METADATA] != ABSENT && rangeEvent[RANGE_FOUND_METADATA] != JsonEvent.VALUE_NULL;

        if (members != ABSENT || value != ABSENT || resumed)
        {
            write(METADATA_OPEN);

            if (members != ABSENT)
            {
                writeEntry(members);
            }
            else if (value != ABSENT)
            {
                write(FORWARDED_PROPS);
                writeEntry(value);
            }

            if (resumed)
            {
                if (members != ABSENT || value != ABSENT)
                {
                    write(COMMA);
                }

                write(AGUI_RESUME_OPEN);
                writeRange(RANGE_FOUND_METADATA);
                write(AGUI_RESUME_CLOSE);
            }

            write(METADATA_CLOSE);
        }
    }

    private void open(
        int index)
    {
        final int offset = retained.offset(index);
        entryLimit = offset + retained.length(index);
        parser.reset();
        parser.wrap(slot, offset, entryLimit, true);
        parser.nextEvent();
    }

    private void pump(
        Walk start)
    {
        walker = start;
        while (walker != walkDone && parser.hasNextEvent())
        {
            walker.walk();
        }
    }

    private void walkDone()
    {
    }

    private void walkSkip()
    {
        switch (parser.nextEvent())
        {
        case START_OBJECT:
        case START_ARRAY:
            skipDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            skipDepth--;
            break;
        default:
            break;
        }

        if (skipDepth == 0)
        {
            walker = skipReturn;
        }
    }

    private void skip(
        JsonEvent event,
        Walk resume)
    {
        if (event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY)
        {
            skipDepth = 1;
            skipReturn = resume;
            walker = walkSkip;
        }
        else
        {
            walker = resume;
        }
    }

    private void walkRaw()
    {
        final JsonEvent event = parser.nextEvent();

        rawFirst = rawFirst == null ? event : rawFirst;

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            rawDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            rawDepth--;
            break;
        default:
            break;
        }

        if (rawDepth == 0)
        {
            rangeStart[rawRange] = rawStart;
            rangeEnd[rawRange] = position();
            rangeEvent[rawRange] = rawFirst;
            walker = rawReturn;
        }
    }

    private void raw(
        int range,
        Walk resume)
    {
        rawRange = range;
        rawStart = position();
        rawDepth = 0;
        rawFirst = null;
        rawReturn = resume;
        walker = walkRaw;
    }

    private void walkIdStart()
    {
        walker = parser.nextEvent() == JsonEvent.START_OBJECT ? walkIdMember : walkDone;
    }

    private void walkIdMember()
    {
        switch (parser.nextEvent())
        {
        case KEY_NAME:
            if (matches(parser.getStringView(), "id"))
            {
                raw(RANGE_ID, walkIdMember);
            }
            else
            {
                parser.skipValue();
            }
            break;
        default:
            walker = walkDone;
            break;
        }
    }

    private void walkMessage()
    {
        walker = parser.nextEvent() == JsonEvent.START_OBJECT ? walkMessageMember : walkDone;
    }

    private void walkMessageMember()
    {
        switch (parser.nextEvent())
        {
        case KEY_NAME:
            if (matches(parser.getStringView(), "content"))
            {
                contentStart = position();
                walker = walkContent;
            }
            else
            {
                parser.skipValue();
            }
            break;
        default:
            walkMessageEnd();
            break;
        }
    }

    private void walkMessageEnd()
    {
        messageIndex = retained.indexOf(Kind.MESSAGE, messageIndex + 1);

        if (messageIndex != ABSENT)
        {
            open(messageIndex);
            walker = walkMessage;
        }
        else
        {
            walker = walkDone;
        }
    }

    private void walkContent()
    {
        final JsonEvent event = parser.nextEvent();

        switch (event)
        {
        case VALUE_STRING:
            separate();
            write(TEXT_OPEN);
            writeRaw(contentStart, position());
            write(OBJECT_CLOSE);
            walker = walkMessageMember;
            break;
        case START_ARRAY:
            walker = walkParts;
            break;
        default:
            skip(event, walkMessageMember);
            break;
        }
    }

    private void walkParts()
    {
        final JsonEvent event = parser.nextEvent();

        switch (event)
        {
        case START_OBJECT:
            resetPart();
            walker = walkPart;
            break;
        case END_ARRAY:
            walker = walkMessageMember;
            break;
        default:
            skip(event, walkParts);
            break;
        }
    }

    private void resetPart()
    {
        partType = PartType.NONE;
        sourceType = SourceType.NONE;
        resetRange(RANGE_TEXT);
        resetRange(RANGE_METADATA);
        resetRange(RANGE_VALUE);
        resetRange(RANGE_MIME_TYPE);
    }

    private void walkPart()
    {
        switch (parser.nextEvent())
        {
        case KEY_NAME:
            final CharSequence name = parser.getStringView();
            if (matches(name, "type"))
            {
                walker = walkPartType;
            }
            else if (matches(name, "text"))
            {
                raw(RANGE_TEXT, walkPart);
            }
            else if (matches(name, "metadata"))
            {
                raw(RANGE_METADATA, walkPart);
            }
            else if (matches(name, "source"))
            {
                walker = walkSourceStart;
            }
            else
            {
                parser.skipValue();
            }
            break;
        default:
            writePart();
            walker = walkParts;
            break;
        }
    }

    private void walkPartType()
    {
        final JsonEvent event = parser.nextEvent();

        if (event == JsonEvent.VALUE_STRING)
        {
            partType = partType(parser.getStringView());
            walker = walkPart;
        }
        else
        {
            skip(event, walkPart);
        }
    }

    private void walkSourceStart()
    {
        final JsonEvent event = parser.nextEvent();

        if (event == JsonEvent.START_OBJECT)
        {
            walker = walkSource;
        }
        else
        {
            skip(event, walkPart);
        }
    }

    private void walkSource()
    {
        switch (parser.nextEvent())
        {
        case KEY_NAME:
            final CharSequence name = parser.getStringView();
            if (matches(name, "type"))
            {
                walker = walkSourceType;
            }
            else if (matches(name, "value"))
            {
                raw(RANGE_VALUE, walkSource);
            }
            else if (matches(name, "mimeType"))
            {
                raw(RANGE_MIME_TYPE, walkSource);
            }
            else
            {
                parser.skipValue();
            }
            break;
        default:
            walker = walkPart;
            break;
        }
    }

    private void walkSourceType()
    {
        final JsonEvent event = parser.nextEvent();

        if (event == JsonEvent.VALUE_STRING)
        {
            sourceType = sourceType(parser.getStringView());
            walker = walkSource;
        }
        else
        {
            skip(event, walkSource);
        }
    }

    private void writePart()
    {
        if (partType == PartType.TEXT && rangeStart[RANGE_TEXT] != ABSENT)
        {
            separate();
            write(TEXT_OPEN);
            writeRange(RANGE_TEXT);
            writePartMetadata();
            write(OBJECT_CLOSE);
        }
        else if (partType == PartType.MEDIA && rangeStart[RANGE_VALUE] != ABSENT && sourceType != SourceType.NONE)
        {
            separate();
            write(sourceType == SourceType.DATA ? RAW_OPEN : URL_OPEN);
            writeRange(RANGE_VALUE);

            if (rangeStart[RANGE_MIME_TYPE] != ABSENT)
            {
                final String mediaType = slot.getStringWithoutLengthUtf8(rangeStart[RANGE_MIME_TYPE] + 1,
                    rangeEnd[RANGE_MIME_TYPE] - rangeStart[RANGE_MIME_TYPE] - 2);

                if (unacceptedMediaType == null && !acceptedMediaType.test(mediaType))
                {
                    unacceptedMediaType = mediaType;
                }

                write(MEDIA_TYPE);
                writeRange(RANGE_MIME_TYPE);
            }

            writePartMetadata();
            write(OBJECT_CLOSE);
        }
    }

    private void writePartMetadata()
    {
        if (rangeStart[RANGE_METADATA] != ABSENT)
        {
            write(PART_METADATA);
            writeRange(RANGE_METADATA);
        }
    }

    private void walkResumeStart()
    {
        walker = parser.nextEvent() == JsonEvent.START_ARRAY ? walkResumeEntries : walkDone;
    }

    private void walkResumeEntries()
    {
        final JsonEvent event = parser.nextEvent();

        switch (event)
        {
        case START_OBJECT:
            entryId = null;
            entryStatus = null;
            resetRange(RANGE_PAYLOAD);
            resetRange(RANGE_RESUME_METADATA);
            walker = walkResumeEntry;
            break;
        case END_ARRAY:
            walker = walkDone;
            break;
        default:
            skip(event, walkResumeEntries);
            break;
        }
    }

    private void walkResumeEntry()
    {
        switch (parser.nextEvent())
        {
        case KEY_NAME:
            final CharSequence name = parser.getStringView();
            if (matches(name, "interruptId"))
            {
                walker = walkResumeId;
            }
            else if (matches(name, "status"))
            {
                walker = walkResumeStatus;
            }
            else if (matches(name, "payload"))
            {
                raw(RANGE_PAYLOAD, walkResumeEntry);
            }
            else if (matches(name, "metadata"))
            {
                raw(RANGE_RESUME_METADATA, walkResumeEntry);
            }
            else
            {
                parser.skipValue();
            }
            break;
        default:
            walkResumeEntryEnd();
            walker = walkResumeEntries;
            break;
        }
    }

    private void walkResumeEntryEnd()
    {
        final boolean matched = resumeIndex == resumeCount || resumeTarget != null && resumeTarget.equals(entryId);

        if (resumeFound == ABSENT && matched)
        {
            resumeFound = resumeCount;
            resumeFoundId = entryId;
            resumeFoundStatus = entryStatus;
            copyRange(RANGE_PAYLOAD, RANGE_FOUND_PAYLOAD);
            copyRange(RANGE_RESUME_METADATA, RANGE_FOUND_METADATA);
        }

        resumeCount++;
    }

    private void walkResumeId()
    {
        final JsonEvent event = parser.nextEvent();

        if (event == JsonEvent.VALUE_STRING)
        {
            entryId = parser.getString();
            walker = walkResumeEntry;
        }
        else
        {
            skip(event, walkResumeEntry);
        }
    }

    private void walkResumeStatus()
    {
        final JsonEvent event = parser.nextEvent();

        if (event == JsonEvent.VALUE_STRING)
        {
            entryStatus = parser.getString();
            walker = walkResumeEntry;
        }
        else
        {
            skip(event, walkResumeEntry);
        }
    }

    private void resetRange(
        int range)
    {
        rangeStart[range] = ABSENT;
        rangeEnd[range] = ABSENT;
        rangeEvent[range] = null;
    }

    private void copyRange(
        int from,
        int to)
    {
        rangeStart[to] = rangeStart[from];
        rangeEnd[to] = rangeEnd[from];
        rangeEvent[to] = rangeEvent[from];
    }

    private int position()
    {
        return entryLimit - parser.remaining();
    }

    private void separate()
    {
        if (partCount++ > 0)
        {
            write(COMMA);
        }
    }

    private void gather(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (length > 0)
        {
            if (gatherCount == gatherBuffers.length)
            {
                gatherBuffers = Arrays.copyOf(gatherBuffers, gatherCount << 1);
                gatherOffsets = Arrays.copyOf(gatherOffsets, gatherCount << 1);
                gatherLengths = Arrays.copyOf(gatherLengths, gatherCount << 1);
            }

            if (buffer == escapeBuffer)
            {
                if (scratchLength + length > scratchBytes.length)
                {
                    scratchBytes = Arrays.copyOf(scratchBytes, Math.max(scratchBytes.length << 1, scratchLength + length));
                }

                buffer.getBytes(offset, scratchBytes, scratchLength, length);
                scratch.wrap(scratchBytes);

                gatherBuffers[gatherCount] = null;
                gatherOffsets[gatherCount] = scratchLength;
                scratchLength += length;
            }
            else
            {
                gatherBuffers[gatherCount] = buffer;
                gatherOffsets[gatherCount] = offset;
            }

            gatherLengths[gatherCount] = length;
            gatherCount++;
            gatherLength += length;
        }
    }

    private void write(
        DirectBufferEx literal)
    {
        sink.write(literal, 0, literal.capacity());
    }

    private void writeRange(
        int range)
    {
        writeRaw(rangeStart[range], rangeEnd[range]);
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
        sink.write(slot, retained.offset(index), retained.length(index));
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

    private static DirectBufferEx literal(
        String text)
    {
        return new UnsafeBufferEx(text.getBytes(UTF_8));
    }
}
