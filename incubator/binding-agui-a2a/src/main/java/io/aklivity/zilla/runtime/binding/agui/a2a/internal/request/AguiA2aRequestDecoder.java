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

import java.util.Arrays;
import java.util.Map;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aMessageAssembler.ResumeStatus;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Append;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Kind;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.request.AguiA2aRetained.Resume;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx.StringEventPositions;

public final class AguiA2aRequestDecoder
{
    public enum Status
    {
        PENDING,
        COMPLETE,
        REJECTED
    }

    private enum Identifier
    {
        THREAD_ID,
        RUN_ID,
        PARENT_RUN_ID
    }

    private enum Role
    {
        UNKNOWN,
        USER,
        TOOL,
        OTHER
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
    private interface Decoder
    {
        void decode();
    }

    @FunctionalInterface
    private interface Recorder
    {
        void record(
            JsonEvent event);
    }

    private static final int TEXT_MAX = 256;
    private static final CharSequence OVERFLOW = "";
    private static final int SEEN_THREAD_ID = 1;
    private static final int SEEN_RUN_ID = 2;
    private static final int SEEN_PARENT_RUN_ID = 4;
    private static final int SEEN_REQUIRED = SEEN_THREAD_ID | SEEN_RUN_ID;
    private static final int ABSENT = -1;
    private static final int STAGE_ENTRY = 3;

    private static final int DEPTH_RESUME_ENTRIES = 1;
    private static final int DEPTH_RESUME_ENTRY_MEMBERS = 2;
    private static final int DEPTH_MESSAGE_MEMBERS = 1;
    private static final int DEPTH_PARTS = 2;
    private static final int DEPTH_PART_MEMBERS = 3;
    private static final int DEPTH_SOURCE_MEMBERS = 4;

    private static final int RANGE_TEXT = 0;
    private static final int RANGE_METADATA = 1;
    private static final int RANGE_VALUE = 2;
    private static final int RANGE_MIME_TYPE = 3;
    private static final int RANGE_ID = 4;
    private static final int RANGE_PAYLOAD = 5;
    private static final int RANGE_RESUME_METADATA = 6;
    private static final int RANGE_TOOL_CALL_ID = 7;
    private static final int RANGE_TOOL_ERROR = 8;
    private static final int RANGE_COUNT = 9;

    private static final String KEY_THREAD_ID = "threadId";
    private static final String KEY_RUN_ID = "runId";
    private static final String KEY_PARENT_RUN_ID = "parentRunId";
    private static final String KEY_MESSAGES = "messages";
    private static final String KEY_STATE = "state";
    private static final String KEY_CONTEXT = "context";
    private static final String KEY_TOOLS = "tools";
    private static final String KEY_TOOL_CALL_ID = "toolCallId";
    private static final String KEY_ERROR = "error";
    private static final String KEY_FORWARDED_PROPS = "forwardedProps";
    private static final String KEY_RESUME = "resume";
    private static final String KEY_ROLE = "role";
    private static final String KEY_ID = "id";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TYPE = "type";
    private static final String KEY_TEXT = "text";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_VALUE = "value";
    private static final String KEY_MIME_TYPE = "mimeType";
    private static final String KEY_INTERRUPT_ID = "interruptId";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PAYLOAD = "payload";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String KEY_RESERVED = "agui";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";

    private final Decoder decodeStart = this::decodeStart;
    private final Decoder decodeMembers = this::decodeMembers;
    private final Decoder decodeId = this::decodeId;
    private final Decoder decodeCapture = this::decodeCapture;
    private final Decoder decodeMessages = this::decodeMessages;
    private final Decoder decodeMessageNext = this::decodeMessageNext;
    private final Decoder decodeMessageCapture = this::decodeMessageCapture;
    private final Decoder decodeMessageDrain = this::decodeMessageDrain;
    private final Decoder decodeMessageRole = this::decodeMessageRole;
    private final Decoder decodeSkip = this::decodeSkip;
    private final Decoder decodeDone = this::decodeDone;
    private final Decoder decodeReject = this::decodeReject;
    private final Decoder decodeIgnore = this::decodeIgnore;

    private final Recorder recordNone = this::recordNone;
    private final Recorder recordResume = this::recordResume;
    private final Recorder recordResumeEntries = this::recordResumeEntries;
    private final Recorder recordResumeEntry = this::recordResumeEntry;
    private final Recorder recordResumeId = this::recordResumeId;
    private final Recorder recordResumeStatus = this::recordResumeStatus;
    private final Recorder recordMessageMember = this::recordMessageMember;
    private final Recorder recordContent = this::recordContent;
    private final Recorder recordParts = this::recordParts;
    private final Recorder recordPart = this::recordPart;
    private final Recorder recordPartType = this::recordPartType;
    private final Recorder recordSourceStart = this::recordSourceStart;
    private final Recorder recordSource = this::recordSource;
    private final Recorder recordSourceType = this::recordSourceType;
    private final Recorder recordRange = this::recordRange;

    private final JsonParserEx parser;
    private final String threadId;
    private final String runId;
    private final String parentRunId;
    private final AguiA2aRetention retention;
    private final AguiA2aRetained retained;
    private final int textMax;
    private final StringBuilder text;
    private final int[] rangeStart = new int[RANGE_COUNT];
    private final int[] rangeEnd = new int[RANGE_COUNT];
    private final JsonEvent[] rangeEvent = new JsonEvent[RANGE_COUNT];

    private Decoder decoder = decodeStart;
    private Decoder messageDecoder = decodeMessageCapture;
    private Recorder recorder = recordNone;
    private Recorder rangeReturn = recordNone;
    private int rangeTarget;
    private int rangeDepth;
    private int[] stage = new int[8 * STAGE_ENTRY];
    private int stageCount;
    private int contentStart;
    private PartType partType = PartType.NONE;
    private SourceType sourceType = SourceType.NONE;
    private String entryId;
    private ResumeStatus entryStatus = ResumeStatus.RESOLVED;
    private AguiA2aRejection rejection;
    private DirectBufferEx window;
    private int windowLimit;
    private int seen;
    private int textLength;
    private CharSequence current;
    private Identifier identifier;
    private Kind captureKind;
    private int captureFrom;
    private int captureMark;
    private int captureEvents;
    private JsonEvent captureFirst;
    private int depth;
    private int historyMark;
    private int historyCount;
    private int messageMark;
    private Role messageRole = Role.UNKNOWN;
    private int oversize;
    private int rejectDepth;
    private int turns;

    public AguiA2aRequestDecoder(
        long streamId,
        String threadId,
        String runId,
        String parentRunId,
        AguiA2aRetention retention)
    {
        this(streamId, threadId, runId, parentRunId, retention, null);
    }

    public AguiA2aRequestDecoder(
        long streamId,
        String threadId,
        String runId,
        String parentRunId,
        AguiA2aRetention retention,
        AguiA2aRetention archiveRetention)
    {
        this.parser = JsonEx.createParser(Map.of(JsonParserEx.STRING_EVENT_POSITIONS, StringEventPositions.QUOTED));
        this.threadId = threadId;
        this.runId = runId;
        this.parentRunId = parentRunId;
        this.retention = retention;
        this.retained = new AguiA2aRetained(retention, streamId);
        this.textMax = Math.max(TEXT_MAX, Math.max(threadId.length(), runId.length()));
        this.text = new StringBuilder(textMax);
    }

    public int decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        try
        {
            window = buffer;
            windowLimit = limit;
            captureFrom = offset;
            parser.wrap(buffer, offset, limit, last);

            while (decoder != decodeDone && decoder != decodeIgnore && parser.hasNextEvent())
            {
                decoder.decode();
            }

            flushCapture();

            if (last && decoder != decodeDone && decoder != decodeIgnore)
            {
                reject(AguiA2aRejection.MALFORMED);
            }
        }
        catch (JsonException | IllegalStateException ex)
        {
            reject(AguiA2aRejection.MALFORMED);
        }

        return decoder == decodeDone || decoder == decodeIgnore ? limit : limit - parser.remaining();
    }

    public Status status()
    {
        return decoder == decodeDone ? Status.COMPLETE :
            decoder == decodeIgnore ? Status.REJECTED :
            Status.PENDING;
    }

    public AguiA2aRejection rejection()
    {
        return rejection;
    }

    public AguiA2aRetained retained()
    {
        return retained;
    }

    public AguiA2aRetained archive()
    {
        return null;
    }

    public String archiveOmitted()
    {
        return null;
    }

    public void releaseArchive()
    {
    }

    public boolean priorTurn()
    {
        return turns > 0;
    }

    private void decodeStart()
    {
        switch (next())
        {
        case START_DOCUMENT:
            break;
        case START_OBJECT:
            decoder = decodeMembers;
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeMembers()
    {
        switch (next())
        {
        case KEY_NAME:
            decodeKey(current);
            break;
        case END_OBJECT:
            decodeFinish();
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeKey(
        CharSequence name)
    {
        if (name == null)
        {
            decoder = decodeMembers;
        }
        else if (matches(name, KEY_THREAD_ID))
        {
            identifier = Identifier.THREAD_ID;
            decoder = decodeId;
        }
        else if (matches(name, KEY_RUN_ID))
        {
            identifier = Identifier.RUN_ID;
            decoder = decodeId;
        }
        else if (matches(name, KEY_PARENT_RUN_ID))
        {
            identifier = Identifier.PARENT_RUN_ID;
            decoder = decodeId;
        }
        else if (matches(name, KEY_MESSAGES))
        {
            decoder = decodeMessages;
        }
        else if (matches(name, KEY_STATE))
        {
            beginCapture(Kind.STATE);
        }
        else if (matches(name, KEY_CONTEXT))
        {
            beginCapture(Kind.CONTEXT);
        }
        else if (matches(name, KEY_TOOLS))
        {
            beginCapture(Kind.TOOLS);
        }
        else if (matches(name, KEY_FORWARDED_PROPS))
        {
            beginCapture(Kind.FORWARDED_PROPS);
        }
        else if (matches(name, KEY_RESUME))
        {
            beginCapture(Kind.RESUME);
        }
        else
        {
            parser.skipValue();
        }
    }

    private void beginCapture(
        Kind kind)
    {
        captureKind = kind;
        captureFrom = position();
        captureMark = retained.mark();
        depth = 0;
        captureEvents = 0;
        captureFirst = null;
        recorder = kind == Kind.RESUME && retained.indexOf(Kind.RESUME) == ABSENT ? recordResume : recordNone;
        decoder = decodeCapture;
    }

    private void decodeId()
    {
        final String expected = switch (identifier)
        {
        case THREAD_ID -> threadId;
        case RUN_ID -> runId;
        case PARENT_RUN_ID -> parentRunId;
        };

        switch (next())
        {
        case VALUE_STRING:
            final CharSequence value = current;
            if (value != null)
            {
                if (expected != null && matches(value, expected))
                {
                    seen |= switch (identifier)
                    {
                    case THREAD_ID -> SEEN_THREAD_ID;
                    case RUN_ID -> SEEN_RUN_ID;
                    case PARENT_RUN_ID -> SEEN_PARENT_RUN_ID;
                    };
                    decoder = decodeMembers;
                }
                else
                {
                    reject(AguiA2aRejection.INVALID_RUN);
                }
            }
            break;
        case VALUE_NULL:
            if (identifier == Identifier.PARENT_RUN_ID && expected == null)
            {
                decoder = decodeMembers;
            }
            else
            {
                reject(identifier == Identifier.PARENT_RUN_ID ? AguiA2aRejection.INVALID_RUN : AguiA2aRejection.MALFORMED);
            }
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeCapture()
    {
        final JsonEvent event = next();

        if (captureEvents++ == 0)
        {
            captureFirst = event;
        }

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            if (captureKind == Kind.FORWARDED_PROPS && depth == 1)
            {
                final CharSequence key = current;
                if (key != null && matches(key, KEY_RESERVED))
                {
                    reject(AguiA2aRejection.RESERVED_KEY);
                }
            }
            break;
        default:
            break;
        }

        recorder.record(event);

        if (decoder == decodeCapture && depth == 0 && event != JsonEvent.KEY_NAME && !parser.deferredBytes())
        {
            decodeCaptured();
        }
    }

    private void decodeCaptured()
    {
        final boolean container = captureFirst == JsonEvent.START_OBJECT || captureFirst == JsonEvent.START_ARRAY;
        final boolean empty = captureFirst == JsonEvent.VALUE_NULL || container && captureEvents == 2;
        final Append result = empty ? Append.OK : retain(captureFrom, position());

        if (result == Append.FULL && captureKind == Kind.TOOLS)
        {
            retained.truncate(captureMark, retained.count());
            decoder = decodeMembers;
        }
        else if (result != Append.OK)
        {
            rejectAppend(result, 1);
        }
        else
        {
            if (empty)
            {
                retained.truncate(captureMark, retained.count());
            }
            else if (captureKind == Kind.FORWARDED_PROPS && captureFirst == JsonEvent.START_OBJECT)
            {
                retained.entryMembers(Kind.FORWARDED_PROPS, captureMark);
            }
            else if (captureKind == Kind.FORWARDED_PROPS)
            {
                retained.entry(Kind.FORWARDED_VALUE, captureMark);
            }
            else
            {
                retained.entry(captureKind, captureMark);
            }

            decoder = decodeMembers;
        }
    }

    private void decodeMessages()
    {
        if (parser.hasNextEvent())
        {
            switch (next())
            {
            case START_ARRAY:
                historyMark = retained.mark();
                historyCount = retained.count();
                decoder = decodeMessageNext;
                break;
            case VALUE_NULL:
                decoder = decodeMembers;
                break;
            default:
                reject(AguiA2aRejection.MALFORMED);
                break;
            }
        }
    }

    private void decodeMessageNext()
    {
        switch (next())
        {
        case START_OBJECT:
            decodeMessageStart();
            break;
        case END_ARRAY:
            if (oversize > 0)
            {
                rejectTooLarge(1);
            }
            else
            {
                decoder = decodeMembers;
            }
            break;
        default:
            reject(AguiA2aRejection.MALFORMED);
            break;
        }
    }

    private void decodeMessageStart()
    {
        captureFrom = position() - 1;
        messageMark = retained.mark();
        messageRole = Role.UNKNOWN;
        depth = 1;
        stageCount = 0;
        partType = PartType.NONE;
        sourceType = SourceType.NONE;
        resetRange(RANGE_ID);
        resetRange(RANGE_TOOL_CALL_ID);
        resetRange(RANGE_TOOL_ERROR);
        recorder = recordMessageMember;
        decoder = decodeMessageCapture;
    }

    private void decodeMessageCapture()
    {
        decodeMessageEvent(next(), decodeMessageCapture);
    }

    private void decodeMessageDrain()
    {
        decodeMessageEvent(next(), decodeMessageDrain);
    }

    private void decodeMessageEvent(
        JsonEvent event,
        Decoder self)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            if (depth == 1)
            {
                final CharSequence key = current;
                if (key != null && matches(key, KEY_ROLE))
                {
                    messageDecoder = self;
                    decoder = decodeMessageRole;
                }
            }
            break;
        default:
            break;
        }

        recorder.record(event);

        if (depth == 0)
        {
            decodeMessageEnd();
        }
    }

    private void decodeMessageRole()
    {
        final JsonEvent event = next();
        final CharSequence value = event == JsonEvent.VALUE_STRING ? current : OVERFLOW;

        if (value != null)
        {
            if (event == JsonEvent.VALUE_STRING && matches(value, ROLE_USER))
            {
                messageRole = Role.USER;
                decoder = messageDecoder;
            }
            else if (event == JsonEvent.VALUE_STRING && matches(value, ROLE_TOOL) && retained.indexOf(Kind.MESSAGE) == -1)
            {
                messageRole = Role.TOOL;
                turns++;
                decoder = messageDecoder;
            }
            else
            {
                messageRole = Role.OTHER;

                if (event == JsonEvent.VALUE_STRING && (matches(value, ROLE_ASSISTANT) || matches(value, ROLE_TOOL)))
                {
                    retained.truncate(historyMark, historyCount);
                    oversize = 0;
                    turns++;
                }
                else if (messageDecoder == decodeMessageCapture)
                {
                    retained.truncate(messageMark, retained.count());
                }

                recorder = recordNone;
                decoder = decodeMessageDrain;
            }
        }
    }

    private void decodeMessageEnd()
    {
        if (decoder == decodeMessageCapture)
        {
            final Append result = retain(captureFrom, position());

            if (result == Append.UNAVAILABLE)
            {
                rejectAppend(result, 0);
            }
            else
            {
                final boolean kept = result == Append.OK &&
                    (messageRole == Role.USER && commitMessage() || messageRole == Role.TOOL && commitToolResult());

                if (!kept)
                {
                    retained.truncate(messageMark, retained.count());
                    oversize += messageRole == Role.USER ? 1 : 0;
                }

                decoder = decodeMessageNext;
            }
        }
        else
        {
            oversize += messageRole == Role.USER ? 1 : 0;
            decoder = decodeMessageNext;
        }
    }

    private boolean commitToolResult()
    {
        final boolean identified = rangeEvent[RANGE_TOOL_CALL_ID] == JsonEvent.VALUE_STRING &&
            rangeEnd[RANGE_TOOL_CALL_ID] != ABSENT;
        final boolean errored = rangeEvent[RANGE_TOOL_ERROR] == JsonEvent.VALUE_STRING && rangeEnd[RANGE_TOOL_ERROR] != ABSENT;
        final boolean fits = identified && retained.accommodates(stageCount / STAGE_ENTRY + (errored ? 3 : 2));

        if (fits)
        {
            retained.range(Kind.TOOL_CALL_ID, rangeStart[RANGE_TOOL_CALL_ID], rangeEnd[RANGE_TOOL_CALL_ID]);

            for (int index = 0; index < stageCount; index += STAGE_ENTRY)
            {
                if (Kind.values()[stage[index]] == Kind.PART_TEXT)
                {
                    retained.range(Kind.TOOL_CONTENT, stage[index + 1], stage[index + 2]);
                }
            }

            if (errored)
            {
                retained.range(Kind.TOOL_ERROR, rangeStart[RANGE_TOOL_ERROR], rangeEnd[RANGE_TOOL_ERROR]);
            }

            retained.entry(Kind.TOOL_RESULT, messageMark);
        }

        return fits;
    }

    private boolean commitMessage()
    {
        final boolean identified = rangeEvent[RANGE_ID] == JsonEvent.VALUE_STRING && rangeEnd[RANGE_ID] != ABSENT;
        final boolean fits = retained.accommodates(stageCount / STAGE_ENTRY + (identified ? 2 : 1));

        if (fits)
        {
            for (int index = 0; index < stageCount; index += STAGE_ENTRY)
            {
                retained.range(Kind.values()[stage[index]], stage[index + 1], stage[index + 2]);
            }

            if (identified)
            {
                retained.range(Kind.MESSAGE_ID, rangeStart[RANGE_ID], rangeEnd[RANGE_ID]);
            }

            retained.entry(Kind.MESSAGE, messageMark);
        }

        return fits;
    }

    private void decodeSkip()
    {
        final JsonEvent event = next();

        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        default:
            break;
        }

        if (depth == 0 && event != JsonEvent.KEY_NAME && !parser.deferredBytes())
        {
            decoder = decodeMembers;
        }
    }

    private void decodeFinish()
    {
        if ((seen & SEEN_REQUIRED) != SEEN_REQUIRED || parentRunId != null && (seen & SEEN_PARENT_RUN_ID) == 0)
        {
            reject(AguiA2aRejection.INVALID_RUN);
        }
        else if (retained.indexOf(Kind.MESSAGE) == -1 && retained.indexOf(Kind.RESUME) == -1 &&
            retained.indexOf(Kind.TOOL_RESULT) == -1)
        {
            reject(AguiA2aRejection.NO_USER_MESSAGE);
        }
        else
        {
            decoder = decodeDone;
        }
    }

    private void decodeDone()
    {
    }

    private void decodeReject()
    {
        switch (next())
        {
        case START_OBJECT:
        case START_ARRAY:
            rejectDepth++;
            break;
        case END_OBJECT:
        case END_ARRAY:
            rejectDepth--;
            break;
        default:
            break;
        }

        if (rejectDepth == 0)
        {
            decoder = decodeIgnore;
        }
    }

    private void decodeIgnore()
    {
    }

    private void flushCapture()
    {
        if (decoder == decodeCapture || decoder == decodeMessageCapture ||
            decoder == decodeMessageRole && messageDecoder == decodeMessageCapture)
        {
            final Append result = retain(captureFrom, position());

            if (result == Append.FULL && decoder == decodeCapture && captureKind == Kind.TOOLS)
            {
                retained.truncate(captureMark, retained.count());
                decoder = decodeSkip;
            }
            else if (result != Append.OK)
            {
                if (decoder == decodeCapture)
                {
                    rejectAppend(result, 1 + depth);
                }
                else if (result == Append.UNAVAILABLE)
                {
                    rejectAppend(result, 0);
                }
                else
                {
                    retained.truncate(messageMark, retained.count());
                    recorder = recordNone;
                    if (decoder == decodeMessageRole)
                    {
                        messageDecoder = decodeMessageDrain;
                    }
                    else
                    {
                        decoder = decodeMessageDrain;
                    }
                }
            }
        }
    }

    private void recordNone(
        JsonEvent event)
    {
    }

    private void recordResume(
        JsonEvent event)
    {
        recorder = event == JsonEvent.START_ARRAY ? recordResumeEntries : recordNone;
    }

    private void recordResumeEntries(
        JsonEvent event)
    {
        if (event == JsonEvent.START_OBJECT && depth == DEPTH_RESUME_ENTRY_MEMBERS)
        {
            entryId = null;
            entryStatus = ResumeStatus.RESOLVED;
            resetRange(RANGE_PAYLOAD);
            resetRange(RANGE_RESUME_METADATA);
            recorder = recordResumeEntry;
        }
    }

    private void recordResumeEntry(
        JsonEvent event)
    {
        final CharSequence key = event == JsonEvent.KEY_NAME && depth == DEPTH_RESUME_ENTRY_MEMBERS ? current : null;

        if (key != null && matches(key, KEY_INTERRUPT_ID))
        {
            recorder = recordResumeId;
        }
        else if (key != null && matches(key, KEY_STATUS))
        {
            recorder = recordResumeStatus;
        }
        else if (key != null && matches(key, KEY_PAYLOAD))
        {
            beginRange(RANGE_PAYLOAD, recordResumeEntry);
        }
        else if (key != null && matches(key, KEY_METADATA))
        {
            beginRange(RANGE_RESUME_METADATA, recordResumeEntry);
        }
        else if (event == JsonEvent.END_OBJECT && depth == DEPTH_RESUME_ENTRIES)
        {
            retained.resume(new Resume(entryId, entryStatus,
                rangeStart[RANGE_PAYLOAD], rangeEnd[RANGE_PAYLOAD], rangeEvent[RANGE_PAYLOAD],
                rangeStart[RANGE_RESUME_METADATA], rangeEnd[RANGE_RESUME_METADATA], rangeEvent[RANGE_RESUME_METADATA]));
            recorder = recordResumeEntries;
        }
    }

    private void recordResumeId(
        JsonEvent event)
    {
        if (event != JsonEvent.VALUE_STRING || current != null)
        {
            entryId = event == JsonEvent.VALUE_STRING ? current.toString() : entryId;
            recorder = recordResumeEntry;
        }
    }

    private void recordResumeStatus(
        JsonEvent event)
    {
        if (event != JsonEvent.VALUE_STRING || current != null)
        {
            entryStatus = event == JsonEvent.VALUE_STRING && matches(current, STATUS_CANCELLED) ?
                ResumeStatus.CANCELLED : entryStatus;
            recorder = recordResumeEntry;
        }
    }

    private void recordMessageMember(
        JsonEvent event)
    {
        final CharSequence key = event == JsonEvent.KEY_NAME && depth == DEPTH_MESSAGE_MEMBERS ? current : null;

        if (key != null && matches(key, KEY_CONTENT))
        {
            contentStart = retainedAt(position());
            recorder = recordContent;
        }
        else if (key != null && matches(key, KEY_ID))
        {
            beginRange(RANGE_ID, recordMessageMember);
        }
        else if (key != null && matches(key, KEY_TOOL_CALL_ID))
        {
            beginRange(RANGE_TOOL_CALL_ID, recordMessageMember);
        }
        else if (key != null && matches(key, KEY_ERROR))
        {
            beginRange(RANGE_TOOL_ERROR, recordMessageMember);
        }
    }

    private void recordContent(
        JsonEvent event)
    {
        if (event == JsonEvent.START_ARRAY)
        {
            recorder = recordParts;
        }
        else if (event != JsonEvent.VALUE_STRING || !parser.deferredBytes())
        {
            if (event == JsonEvent.VALUE_STRING)
            {
                stage(Kind.PART_TEXT, contentStart, retainedAt(position()));
            }

            recorder = recordMessageMember;
        }
    }

    private void recordParts(
        JsonEvent event)
    {
        if (event == JsonEvent.START_OBJECT && depth == DEPTH_PART_MEMBERS)
        {
            partType = PartType.NONE;
            sourceType = SourceType.NONE;
            resetRange(RANGE_TEXT);
            resetRange(RANGE_METADATA);
            resetRange(RANGE_VALUE);
            resetRange(RANGE_MIME_TYPE);
            recorder = recordPart;
        }
        else if (event == JsonEvent.END_ARRAY && depth == DEPTH_MESSAGE_MEMBERS)
        {
            recorder = recordMessageMember;
        }
    }

    private void recordPart(
        JsonEvent event)
    {
        final CharSequence key = event == JsonEvent.KEY_NAME && depth == DEPTH_PART_MEMBERS ? current : null;

        if (key != null && matches(key, KEY_TYPE))
        {
            recorder = recordPartType;
        }
        else if (key != null && matches(key, KEY_TEXT))
        {
            beginRange(RANGE_TEXT, recordPart);
        }
        else if (key != null && matches(key, KEY_METADATA))
        {
            beginRange(RANGE_METADATA, recordPart);
        }
        else if (key != null && matches(key, KEY_SOURCE))
        {
            recorder = recordSourceStart;
        }
        else if (event == JsonEvent.END_OBJECT && depth == DEPTH_PARTS)
        {
            stagePart();
            recorder = recordParts;
        }
    }

    private void recordPartType(
        JsonEvent event)
    {
        if (event != JsonEvent.VALUE_STRING || current != null)
        {
            partType = event == JsonEvent.VALUE_STRING ? partType(current) : partType;
            recorder = recordPart;
        }
    }

    private void recordSourceStart(
        JsonEvent event)
    {
        recorder = event == JsonEvent.START_OBJECT ? recordSource : recordPart;
    }

    private void recordSource(
        JsonEvent event)
    {
        final CharSequence key = event == JsonEvent.KEY_NAME && depth == DEPTH_SOURCE_MEMBERS ? current : null;

        if (key != null && matches(key, KEY_TYPE))
        {
            recorder = recordSourceType;
        }
        else if (key != null && matches(key, KEY_VALUE))
        {
            beginRange(RANGE_VALUE, recordSource);
        }
        else if (key != null && matches(key, KEY_MIME_TYPE))
        {
            beginRange(RANGE_MIME_TYPE, recordSource);
        }
        else if (event == JsonEvent.END_OBJECT && depth == DEPTH_PART_MEMBERS)
        {
            recorder = recordPart;
        }
    }

    private void recordSourceType(
        JsonEvent event)
    {
        if (event != JsonEvent.VALUE_STRING || current != null)
        {
            sourceType = event == JsonEvent.VALUE_STRING ? sourceType(current) : sourceType;
            recorder = recordSource;
        }
    }

    private void recordRange(
        JsonEvent event)
    {
        rangeEvent[rangeTarget] = rangeEvent[rangeTarget] == null ? event : rangeEvent[rangeTarget];

        if (depth == rangeDepth && !parser.deferredBytes())
        {
            rangeEnd[rangeTarget] = retainedAt(position());
            recorder = rangeReturn;
        }
    }

    private void beginRange(
        int range,
        Recorder resume)
    {
        this.rangeTarget = range;
        this.rangeDepth = depth;
        this.rangeReturn = resume;
        rangeStart[range] = retainedAt(position());
        rangeEnd[range] = ABSENT;
        rangeEvent[range] = null;
        recorder = recordRange;
    }

    private void resetRange(
        int range)
    {
        rangeStart[range] = ABSENT;
        rangeEnd[range] = ABSENT;
        rangeEvent[range] = null;
    }

    private boolean present(
        int range)
    {
        return rangeEnd[range] != ABSENT;
    }

    private void stagePart()
    {
        if (partType == PartType.TEXT && present(RANGE_TEXT))
        {
            stageRange(Kind.PART_TEXT, RANGE_TEXT);
            stageRange(Kind.PART_METADATA, RANGE_METADATA);
        }
        else if (partType == PartType.MEDIA && present(RANGE_VALUE) && sourceType != SourceType.NONE)
        {
            stageRange(sourceType == SourceType.DATA ? Kind.PART_RAW : Kind.PART_URL, RANGE_VALUE);
            stageRange(Kind.PART_MIME, RANGE_MIME_TYPE);
            stageRange(Kind.PART_METADATA, RANGE_METADATA);
        }
    }

    private void stageRange(
        Kind kind,
        int range)
    {
        if (present(range))
        {
            stage(kind, rangeStart[range], rangeEnd[range]);
        }
    }

    private void stage(
        Kind kind,
        int start,
        int end)
    {
        if (stageCount + STAGE_ENTRY > stage.length)
        {
            stage = Arrays.copyOf(stage, stage.length << 1);
        }

        stage[stageCount++] = kind.ordinal();
        stage[stageCount++] = start;
        stage[stageCount++] = end;
    }

    private int retainedAt(
        int position)
    {
        return retained.mark() + position - captureFrom;
    }

    private JsonEvent next()
    {
        final JsonEvent event = parser.nextEvent();
        current = switch (event)
        {
        case KEY_NAME, VALUE_STRING, VALUE_NUMBER -> token();
        default -> null;
        };
        return event;
    }

    private CharSequence token()
    {
        final CharSequence view = parser.getStringView();
        final boolean fragment = parser.deferredBytes();
        CharSequence token = view;

        if (fragment || textLength > 0)
        {
            if (textLength == 0)
            {
                text.setLength(0);
            }

            if (textLength + view.length() <= textMax)
            {
                text.append(view);
            }

            textLength += view.length();
            token = fragment ? null : textLength <= textMax ? text : OVERFLOW;
            textLength = fragment ? textLength : 0;
        }

        if (fragment)
        {
            parser.consumed(view.length());
        }

        return token;
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

    private Append retain(
        int from,
        int to)
    {
        return from < to ? retained.append(window, from, to - from) : Append.OK;
    }

    private int position()
    {
        return windowLimit - parser.remaining();
    }

    private void rejectAppend(
        Append result,
        int nesting)
    {
        if (result == Append.UNAVAILABLE)
        {
            reject(AguiA2aRejection.RETENTION_UNAVAILABLE);
        }
        else
        {
            rejectTooLarge(nesting);
        }
    }

    private void rejectTooLarge(
        int nesting)
    {
        this.rejection = AguiA2aRejection.TOO_LARGE;
        this.rejectDepth = nesting;
        this.decoder = decodeReject;
        retained.release();
    }

    private void reject(
        AguiA2aRejection rejection)
    {
        this.rejection = rejection;
        this.decoder = decodeIgnore;
        retained.release();
    }
}
