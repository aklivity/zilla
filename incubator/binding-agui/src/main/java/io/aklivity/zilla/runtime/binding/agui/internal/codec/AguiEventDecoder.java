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

import static io.aklivity.zilla.runtime.engine.util.Flags.FIN;
import static io.aklivity.zilla.runtime.engine.util.Flags.INIT;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiActivityType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiContentType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEncryptedSubtype;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiRole;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStateType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStepType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentOutcome;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentType;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public final class AguiEventDecoder
{
    public static final String CODE_INVALID_MESSAGE = "invalid_message";
    public static final String CODE_INVALID_SEQUENCE = "invalid_sequence";

    public static final int EX_MAX = 1024;
    private static final int CARRY_MAX = 16;
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_OUTCOME = "outcome";
    private static final String ERROR_MALFORMED = "Malformed event";
    private static final String ERROR_TOO_LARGE = "Event too large";
    private static final String ERROR_TRUNCATED = "Truncated event";

    private static final Map<String, AguiRole> ROLES = Map.of(
        "assistant", AguiRole.ASSISTANT,
        "developer", AguiRole.DEVELOPER,
        "system", AguiRole.SYSTEM,
        "user", AguiRole.USER);

    private enum State
    {
        IDLE,
        ASSEMBLING,
        STARTING,
        STREAMING,
        TAIL,
        FAILED
    }

    private enum RunState
    {
        NEW,
        STARTED,
        FINISHED
    }

    private enum GroupState
    {
        NONE,
        OPENED,
        EMITTED
    }

    private enum Scan
    {
        COMPLETE,
        INCOMPLETE,
        MALFORMED
    }

    private enum PayloadMode
    {
        NONE,
        RAW,
        TEXT
    }

    private record Member(
        String name,
        int valueStart,
        int valueEnd)
    {
    }

    private static final class Rejection extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private final String code;

        private Rejection(
            String code,
            String message)
        {
            super(message, null, false, false);
            this.code = code;
        }
    }

    private final AguiEventSink sink;
    private final int typeId;
    private final int maximum;
    private final MutableDirectBufferEx eventBuffer;
    private final MutableDirectBufferEx holdBuffer;
    private final MutableDirectBufferEx payloadBuffer;
    private final MutableDirectBufferEx pendingExBuffer;
    private final MutableDirectBufferEx carryBuffer;
    private final MutableDirectBufferEx scratchBuffer;
    private final AguiDataExFW.Builder dataExRW = new AguiDataExFW.Builder();
    private final AguiDataExFW dataExRO = new AguiDataExFW();
    private final AguiValueExtent extent = new AguiValueExtent();
    private final List<Member> members = new ArrayList<>();
    private final Set<String> steps = new HashSet<>();
    private final Set<String> subagents = new HashSet<>();

    private State state = State.IDLE;
    private RunState run = RunState.NEW;
    private GroupState group = GroupState.NONE;
    private String groupId;
    private AguiEventType openType;
    private String openId;
    private AguiEventType eventType;
    private PayloadMode streamMode = PayloadMode.NONE;
    private int eventLength;
    private int pendingExLength;
    private int holdLength;
    private int carryLength;

    public AguiEventDecoder(
        AguiEventSink sink,
        int typeId,
        int maximum)
    {
        this.sink = sink;
        this.typeId = typeId;
        this.maximum = maximum;
        this.eventBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.holdBuffer = new UnsafeBufferEx(new byte[maximum]);
        this.payloadBuffer = new UnsafeBufferEx(new byte[maximum + CARRY_MAX]);
        this.pendingExBuffer = new UnsafeBufferEx(new byte[maximum + EX_MAX]);
        this.carryBuffer = new UnsafeBufferEx(new byte[CARRY_MAX]);
        this.scratchBuffer = new UnsafeBufferEx(new byte[maximum + CARRY_MAX]);
    }

    public void decode(
        int flags,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final boolean init = (flags & INIT) != 0;
        final boolean fin = (flags & FIN) != 0;

        if (state == State.IDLE && init)
        {
            state = State.ASSEMBLING;
            eventLength = 0;
        }
        else if (state == State.IDLE || init && state != State.FAILED)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }

        switch (state)
        {
        case ASSEMBLING:
            decodeAssembling(fin, buffer, offset, limit);
            break;
        case STREAMING:
        case TAIL:
            decodeStreaming(fin, buffer, offset, limit);
            break;
        default:
            break;
        }
    }

    public void finish()
    {
        if (state == State.ASSEMBLING || state == State.STREAMING || state == State.TAIL)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_TRUNCATED);
        }
        else if (state == State.IDLE && run != RunState.FINISHED)
        {
            fail(CODE_INVALID_SEQUENCE, "Run not finished");
        }
    }

    private void decodeAssembling(
        boolean fin,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int before = eventLength;
        final int appended = Math.min(maximum - eventLength, limit - offset);
        eventBuffer.putBytes(eventLength, buffer, offset, appended);
        eventLength += appended;

        final boolean whole = appended == limit - offset;
        final Scan scan = scanMembers();

        if (scan == Scan.MALFORMED || scan == Scan.INCOMPLETE && whole && fin || scan == Scan.COMPLETE && !whole)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }
        else if (scan == Scan.COMPLETE && fin)
        {
            decodeEvent();
        }
        else if (scan == Scan.INCOMPLETE && streamable())
        {
            startStreaming(fin, before, buffer, offset, limit);
        }
        else if (!whole)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_TOO_LARGE);
        }
    }

    private void decodeStreaming(
        boolean fin,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        if (state == State.STREAMING)
        {
            streamChunk(buffer, offset, limit);
        }

        if (fin)
        {
            endStreaming();
        }
    }

    private void endStreaming()
    {
        if (state == State.TAIL)
        {
            state = State.IDLE;
        }
        else if (state == State.STREAMING)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }
    }

    private boolean streamable()
    {
        final Member typeMember = member(MEMBER_TYPE);
        final AguiEventType type = typeMember != null && typeMember.valueEnd() >= 0 && stringValue(typeMember) != null
            ? AguiEventType.of(stringValue(typeMember))
            : null;
        final Member payload = type != null && type.streamable ? member(type.payload) : null;

        eventType = type;

        return payload != null && payload.valueEnd() < 0;
    }

    private void startStreaming(
        boolean fin,
        int before,
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final Member payload = member(eventType.payload);
        final int start = payload.valueStart();

        try
        {
            gate(eventType);
            streamMode = prepare(eventType, true);

            extent.reset();
            carryLength = 0;
            state = State.STARTING;

            if (start >= before)
            {
                streamChunk(buffer, offset + start - before, limit);
            }
            else
            {
                streamChunk(eventBuffer, start, before);

                if (state == State.STREAMING)
                {
                    streamChunk(buffer, offset, limit);
                }
            }

            if (fin)
            {
                endStreaming();
            }
        }
        catch (Rejection ex)
        {
            fail(ex.code, ex.getMessage());
        }
    }

    private void streamChunk(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final int consumed = extent.advance(buffer, offset, limit);

        if (consumed == AguiValueExtent.MALFORMED)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }
        else
        {
            final boolean first = state == State.STARTING;
            final boolean text = streamMode == PayloadMode.TEXT;
            final boolean complete = extent.complete();
            final int from = offset + (text && first ? 1 : 0);
            final int to = offset + consumed - (text && complete ? 1 : 0);

            final int flags = (first ? INIT : 0) | (complete ? FIN : 0);
            final int length = text ? unescapeChunk(buffer, from, to, complete) : to - from;

            if (length != AguiJsonUnescape.MALFORMED)
            {
                if (flags != 0 || length > 0)
                {
                    final DirectBufferEx payload = text ? payloadBuffer : buffer;
                    final int payloadOffset = text ? 0 : from;

                    sink.message(flags, first ? pendingEx() : null, payload, payloadOffset, payloadOffset + length);
                }

                pendingExLength = 0;
                state = complete ? State.TAIL : State.STREAMING;
            }
        }
    }

    private int unescapeChunk(
        DirectBufferEx buffer,
        int from,
        int to,
        boolean complete)
    {
        scratchBuffer.putBytes(0, carryBuffer, 0, carryLength);
        scratchBuffer.putBytes(carryLength, buffer, from, Math.max(to - from, 0));
        final int available = carryLength + Math.max(to - from, 0);

        final int whole = AguiJsonUnescape.completeLength(scratchBuffer, 0, available);
        int length = AguiJsonUnescape.MALFORMED;

        if (whole == AguiJsonUnescape.MALFORMED || complete && whole != available)
        {
            fail(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }
        else
        {
            length = AguiJsonUnescape.unescape(scratchBuffer, 0, whole, payloadBuffer, 0);
            carryLength = available - whole;
            carryBuffer.putBytes(0, scratchBuffer, whole, carryLength);
        }

        return length;
    }

    private Scan scanMembers()
    {
        members.clear();

        Scan scan = Scan.INCOMPLETE;
        int progress = skipWhitespace(0);

        if (progress < eventLength)
        {
            scan = eventBuffer.getByte(progress) == '{' ? scanObject(progress + 1) : Scan.MALFORMED;
        }

        return scan;
    }

    private Scan scanObject(
        int start)
    {
        Scan scan = Scan.INCOMPLETE;
        int progress = skipWhitespace(start);
        boolean first = true;

        scanning:
        while (progress < eventLength)
        {
            final byte octet = eventBuffer.getByte(progress);

            if (octet == '}' && first)
            {
                scan = trailing(progress + 1);
                break;
            }

            if (octet != '"')
            {
                scan = Scan.MALFORMED;
                break;
            }

            final int keyEnd = stringEnd(progress);

            if (keyEnd < 0)
            {
                break;
            }

            final String name = stringValue(progress, keyEnd);
            progress = skipWhitespace(keyEnd);

            if (progress >= eventLength)
            {
                break;
            }

            if (eventBuffer.getByte(progress) != ':')
            {
                scan = Scan.MALFORMED;
                break;
            }

            final int valueStart = skipWhitespace(progress + 1);

            if (valueStart >= eventLength)
            {
                break;
            }

            extent.reset();
            final int length = extent.advance(eventBuffer, valueStart, eventLength);

            if (length == AguiValueExtent.MALFORMED)
            {
                scan = Scan.MALFORMED;
                break;
            }

            if (!extent.complete())
            {
                members.add(new Member(name, valueStart, -1));
                break;
            }

            members.add(new Member(name, valueStart, valueStart + length));
            progress = skipWhitespace(valueStart + length);

            if (progress >= eventLength)
            {
                break;
            }

            switch (eventBuffer.getByte(progress))
            {
            case ',':
                progress = skipWhitespace(progress + 1);
                first = false;
                break;
            case '}':
                scan = trailing(progress + 1);
                break scanning;
            default:
                scan = Scan.MALFORMED;
                break scanning;
            }
        }

        return scan;
    }

    private Scan trailing(
        int start)
    {
        return skipWhitespace(start) == eventLength ? Scan.COMPLETE : Scan.MALFORMED;
    }

    private int skipWhitespace(
        int start)
    {
        int progress = start;

        while (progress < eventLength && isWhitespace(eventBuffer.getByte(progress)))
        {
            progress++;
        }

        return progress;
    }

    private static boolean isWhitespace(
        byte octet)
    {
        return octet == ' ' || octet == '\n' || octet == '\r' || octet == '\t';
    }

    private int stringEnd(
        int start)
    {
        int progress = start + 1;
        boolean escaped = false;
        int end = -1;

        while (end < 0 && progress < eventLength)
        {
            final byte octet = eventBuffer.getByte(progress++);

            if (escaped)
            {
                escaped = false;
            }
            else if (octet == '\\')
            {
                escaped = true;
            }
            else if (octet == '"')
            {
                end = progress;
            }
        }

        return end;
    }

    private String stringValue(
        int start,
        int end)
    {
        final int length = AguiJsonUnescape.unescape(eventBuffer, start + 1, end - 1, scratchBuffer, 0);

        return scratchBuffer.getStringWithoutLengthUtf8(0, length);
    }

    private String stringValue(
        Member member)
    {
        String value = null;

        if (member.valueEnd() >= 0 && eventBuffer.getByte(member.valueStart()) == '"')
        {
            final int whole = AguiJsonUnescape.completeLength(eventBuffer, member.valueStart() + 1, member.valueEnd() - 1);

            if (whole == member.valueEnd() - member.valueStart() - 2)
            {
                value = stringValue(member.valueStart(), member.valueEnd());
            }
        }

        return value;
    }

    private Member member(
        String name)
    {
        Member found = null;

        for (int index = 0; found == null && index < members.size(); index++)
        {
            final Member candidate = members.get(index);

            if (candidate.name().equals(name))
            {
                found = candidate;
            }
        }

        return found;
    }

    private boolean isNull(
        Member member)
    {
        return member.valueEnd() - member.valueStart() == 4 &&
            eventBuffer.getStringWithoutLengthUtf8(member.valueStart(), 4).equals("null");
    }

    private String optional(
        String name)
    {
        final Member member = member(name);
        String value = null;

        if (member != null && !isNull(member))
        {
            value = stringValue(member);

            if (value == null)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return value;
    }

    private String required(
        String name)
    {
        final String value = optional(name);

        if (value == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: " + name);
        }

        return value;
    }

    private long timestamp()
    {
        final Member member = member("timestamp");
        long timestamp = -1L;

        if (member != null && !isNull(member))
        {
            try
            {
                timestamp = Long.parseLong(eventBuffer.getStringWithoutLengthUtf8(member.valueStart(),
                    member.valueEnd() - member.valueStart()));
            }
            catch (NumberFormatException ex)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }
        }

        return timestamp;
    }

    private AguiEventMetaFW.Builder meta(
        AguiEventMetaFW.Builder meta,
        boolean attribution)
    {
        final Member metadata = member("metadata");
        final AguiEventMetaFW.Builder timed = meta
            .timestamp(timestamp())
            .subagentRunId(attribution ? optional("subagentRunId") : null);

        if (metadata != null && !isNull(metadata))
        {
            timed.metadataLength(metadata.valueEnd() - metadata.valueStart())
                .metadata(eventBuffer, metadata.valueStart(), metadata.valueEnd() - metadata.valueStart());
        }

        return timed;
    }

    private AguiDataExFW pendingEx()
    {
        return dataExRO.wrap(pendingExBuffer, 0, pendingExLength);
    }

    private void built(
        AguiDataExFW.Builder builder)
    {
        pendingExLength = builder.build().limit();
    }

    private AguiDataExFW.Builder ex()
    {
        return dataExRW.wrap(pendingExBuffer, 0, pendingExBuffer.capacity()).typeId(typeId);
    }

    private void fail(
        String code,
        String message)
    {
        state = State.FAILED;
        sink.failed(code, message);
    }

    private void sequence(
        String message)
    {
        throw new Rejection(CODE_INVALID_SEQUENCE, message);
    }

    private void decodeEvent()
    {
        state = State.IDLE;

        try
        {
            final Member typeMember = member(MEMBER_TYPE);

            if (typeMember == null)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, "Missing event type");
            }

            final String name = stringValue(typeMember);

            if (name == null)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
            }

            final AguiEventType type = AguiEventType.of(name);

            if (type == null)
            {
                throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported event type: " + name);
            }

            decodeEvent(type);
        }
        catch (Rejection ex)
        {
            fail(ex.code, ex.getMessage());
        }
    }

    private void decodeEvent(
        AguiEventType type)
    {
        if (run == RunState.FINISHED)
        {
            sequence("Event after run finished");
        }

        if (run == RunState.NEW && type != AguiEventType.RUN_STARTED && type != AguiEventType.RUN_ERROR)
        {
            sequence("Event before run started");
        }

        switch (type)
        {
        case RUN_STARTED:
            decodeRunStarted();
            break;
        case RUN_FINISHED:
            decodeRunFinished();
            break;
        case RUN_ERROR:
            decodeRunError();
            break;
        case TEXT_MESSAGE_START:
            openMessage(type, required("messageId"), textEx());
            break;
        case TEXT_MESSAGE_CONTENT:
        case TEXT_MESSAGE_END:
            decodeContent(type, AguiEventType.TEXT_MESSAGE_START, "messageId", "Text message not started: ");
            break;
        case TOOL_CALL_START:
            openMessage(type, required("toolCallId"), toolCallEx());
            break;
        case TOOL_CALL_ARGS:
        case TOOL_CALL_END:
            decodeContent(type, AguiEventType.TOOL_CALL_START, "toolCallId", "Tool call not started: ");
            break;
        case REASONING_START:
            decodeReasoningStart();
            break;
        case REASONING_MESSAGE_START:
            decodeReasoningMessageStart();
            break;
        case REASONING_MESSAGE_CONTENT:
        case REASONING_MESSAGE_END:
            decodeContent(type, AguiEventType.REASONING_MESSAGE_START, "messageId", "Reasoning message not started: ");
            break;
        case REASONING_END:
            decodeReasoningEnd();
            break;
        default:
            decodePayloadEvent(type);
            break;
        }
    }

    private void decodeRunStarted()
    {
        if (run == RunState.STARTED)
        {
            sequence("Run already started");
        }

        final String threadId = required("threadId");
        final String runId = required("runId");
        final String parentRunId = optional("parentRunId");

        run = RunState.STARTED;
        sink.runStarted(threadId, runId, parentRunId);
    }

    private void decodeRunFinished()
    {
        if (openType != null)
        {
            sequence("Run finished with open message: " + openId);
        }

        if (group != GroupState.NONE)
        {
            sequence("Run finished with open reasoning: " + groupId);
        }

        if (!steps.isEmpty())
        {
            sequence("Run finished with unfinished steps");
        }

        if (!subagents.isEmpty())
        {
            sequence("Run finished with unfinished subagents");
        }

        final long timestamp = timestamp();
        final JsonObject event = readObject();
        final JsonObjectBuilder detail = Json.createObjectBuilder();
        AguiOutcomeType outcome = AguiOutcomeType.NONE;

        for (Map.Entry<String, JsonValue> entry : event.entrySet())
        {
            switch (entry.getKey())
            {
            case MEMBER_TYPE:
            case "threadId":
            case "runId":
            case "timestamp":
                break;
            case MEMBER_OUTCOME:
                outcome = outcome(entry.getValue(), detail);
                break;
            default:
                detail.add(entry.getKey(), entry.getValue());
                break;
            }
        }

        final JsonObject detailObject = detail.build();

        run = RunState.FINISHED;
        sink.runFinished(outcome, timestamp, detailObject.isEmpty() ? null : detailObject.toString());
    }

    private AguiOutcomeType outcome(
        JsonValue value,
        JsonObjectBuilder detail)
    {
        if (!(value instanceof JsonObject object) || !(object.get(MEMBER_TYPE) instanceof JsonString type))
        {
            throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }

        final AguiOutcomeType outcome;

        switch (type.getString())
        {
        case "success":
            outcome = AguiOutcomeType.SUCCESS;
            break;
        case "interrupt":
            outcome = AguiOutcomeType.INTERRUPT;
            break;
        case "cancelled":
            outcome = AguiOutcomeType.CANCELLED;
            break;
        default:
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported outcome type: " + type.getString());
        }

        final JsonObjectBuilder specific = Json.createObjectBuilder();
        object.forEach((name, member) ->
        {
            if (!MEMBER_TYPE.equals(name))
            {
                specific.add(name, member);
            }
        });

        final JsonObject specifics = specific.build();

        if (!specifics.isEmpty())
        {
            detail.add(MEMBER_OUTCOME, specifics);
        }

        return outcome;
    }

    private JsonObject readObject()
    {
        try
        {
            return Json.createReader(new StringReader(eventBuffer.getStringWithoutLengthUtf8(0, eventLength))).readObject();
        }
        catch (JsonException | IllegalStateException ex)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, ERROR_MALFORMED);
        }
    }

    private void decodeRunError()
    {
        final String message = required("message");
        final String code = optional("code");

        emitOpen(false);
        run = RunState.FINISHED;
        sink.runError(message, code);
    }

    private void requireNoOpenMessage()
    {
        if (openType != null)
        {
            sequence("Message already open: " + openId);
        }
    }

    private void openMessage(
        AguiEventType type,
        String id,
        AguiDataExFW.Builder builder)
    {
        requireNoOpenMessage();

        built(builder);
        openType = type;
        openId = id;
        holdLength = 0;
    }

    private void decodeContent(
        AguiEventType type,
        AguiEventType opened,
        String idName,
        String error)
    {
        final String id = required(idName);

        if (openType != opened || !openId.equals(id))
        {
            sequence(error + id);
        }

        if (type.payload != null)
        {
            final String delta = required(type.payload);

            if (!delta.isEmpty())
            {
                if (holdLength > 0)
                {
                    emitOpen(false);
                }

                holdLength = AguiJsonUnescape.unescape(eventBuffer, member(type.payload).valueStart() + 1,
                    member(type.payload).valueEnd() - 1, holdBuffer, 0);
            }
        }
        else
        {
            emitOpen(true);
            openType = null;
            openId = null;
        }
    }

    private void emitOpen(
        boolean last)
    {
        final int flags = (pendingExLength > 0 ? INIT : 0) | (last ? FIN : 0);

        if (holdLength > 0 || flags != 0)
        {
            sink.message(flags, pendingExLength > 0 ? pendingEx() : null, holdBuffer, 0, holdLength);
            pendingExLength = 0;
            holdLength = 0;
        }
    }

    private void decodeReasoningStart()
    {
        if (group != GroupState.NONE)
        {
            sequence("Reasoning already started: " + groupId);
        }

        groupId = required("messageId");
        group = GroupState.OPENED;
    }

    private void decodeReasoningMessageStart()
    {
        if (group == GroupState.NONE)
        {
            sequence("Reasoning not started");
        }

        requireNoOpenMessage();

        final String id = required("messageId");
        final String grouped = groupId.equals(id) ? null : groupId;

        openMessage(AguiEventType.REASONING_MESSAGE_START, id,
            ex().reasoning(r -> r.meta(m -> meta(m, true)).messageId(id).groupId(grouped)));
        group = GroupState.EMITTED;
    }

    private void decodeReasoningEnd()
    {
        final String id = required("messageId");

        if (group == GroupState.NONE)
        {
            sequence("Reasoning not started");
        }

        if (!groupId.equals(id))
        {
            sequence("Reasoning not started: " + id);
        }

        if (openType != null)
        {
            sequence("Message already open: " + openId);
        }

        emitGroup();
        group = GroupState.NONE;
        groupId = null;
    }

    private void emitGroup()
    {
        if (group == GroupState.OPENED)
        {
            built(ex().reasoning(r -> r.meta(m -> m.timestamp(-1)).messageId(groupId)));
            sink.message(INIT | FIN, pendingEx(), holdBuffer, 0, 0);
            pendingExLength = 0;
            group = GroupState.EMITTED;
        }
    }

    private void decodePayloadEvent(
        AguiEventType type)
    {
        gate(type);

        final PayloadMode mode = prepare(type, false);
        final int length;

        switch (mode)
        {
        case RAW:
            final Member raw = member(type.payload);
            sink.message(INIT | FIN, pendingEx(), eventBuffer, raw.valueStart(), raw.valueEnd());
            break;
        case TEXT:
            final Member text = member(type.payload);
            length = AguiJsonUnescape.unescape(eventBuffer, text.valueStart() + 1, text.valueEnd() - 1, payloadBuffer, 0);
            sink.message(INIT | FIN, pendingEx(), payloadBuffer, 0, length);
            break;
        default:
            sink.message(INIT | FIN, pendingEx(), eventBuffer, 0, 0);
            break;
        }

        pendingExLength = 0;
    }

    private void gate(
        AguiEventType type)
    {
        if (run == RunState.FINISHED)
        {
            sequence("Event after run finished");
        }

        if (run == RunState.NEW)
        {
            sequence("Event before run started");
        }

        requireNoOpenMessage();
    }

    private PayloadMode prepare(
        AguiEventType type,
        boolean streaming)
    {
        final PayloadMode mode;

        switch (type)
        {
        case REASONING_ENCRYPTED_VALUE:
            mode = prepareEncrypted();
            break;
        case TOOL_CALL_RESULT:
            mode = prepareToolResult();
            break;
        case STATE_SNAPSHOT:
            built(ex().state(s -> s.meta(m -> meta(m, true)).type(t -> t.set(AguiStateType.SNAPSHOT))));
            mode = PayloadMode.RAW;
            break;
        case STATE_DELTA:
            built(ex().state(s -> s.meta(m -> meta(m, true)).type(t -> t.set(AguiStateType.DELTA))));
            mode = PayloadMode.RAW;
            break;
        case MESSAGES_SNAPSHOT:
            built(ex().state(s -> s.meta(m -> meta(m, true)).type(t -> t.set(AguiStateType.MESSAGES_SNAPSHOT))));
            mode = PayloadMode.RAW;
            break;
        case ACTIVITY_SNAPSHOT:
        case ACTIVITY_DELTA:
            mode = prepareActivity(type);
            break;
        case STEP_STARTED:
        case STEP_FINISHED:
            mode = prepareStep(type);
            break;
        case SUBAGENT_STARTED:
        case SUBAGENT_FINISHED:
        case SUBAGENT_ERROR:
            mode = prepareSubagent(type);
            break;
        case RAW:
            final String source = optional("source");
            built(ex().raw(r -> r.meta(m -> meta(m, true)).source(source)));
            mode = PayloadMode.RAW;
            break;
        default:
            final String name = required("name");
            built(ex().custom(c -> c.meta(m -> meta(m, true)).name(name)));
            mode = PayloadMode.RAW;
            break;
        }

        if (mode == PayloadMode.RAW && member(type.payload) == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: " + type.payload);
        }

        return mode;
    }

    private PayloadMode prepareEncrypted()
    {
        if (group == GroupState.NONE)
        {
            sequence("Reasoning not started");
        }

        emitGroup();

        final String subtype = required("subtype");
        final String entityId = required("entityId");
        final AguiEncryptedSubtype encrypted;

        switch (subtype)
        {
        case "message":
            encrypted = AguiEncryptedSubtype.MESSAGE;
            break;
        case "tool-call":
            encrypted = AguiEncryptedSubtype.TOOL_CALL;
            break;
        default:
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported subtype: " + subtype);
        }

        if (member("encryptedValue") == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: encryptedValue");
        }

        built(ex().reasoningEncrypted(r -> r.meta(m -> meta(m, true)).subtype(t -> t.set(encrypted)).entityId(entityId)));

        return PayloadMode.TEXT;
    }

    private PayloadMode prepareToolResult()
    {
        final String messageId = required("messageId");
        final String toolCallId = required("toolCallId");
        final Member content = member("content");

        if (content == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Missing member: content");
        }

        final byte first = eventBuffer.getByte(content.valueStart());
        final AguiContentType type;

        switch (first)
        {
        case '"':
            type = AguiContentType.TEXT;
            break;
        case '[':
            type = AguiContentType.PARTS;
            break;
        default:
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported tool result content");
        }

        built(ex().toolResult(r -> r.meta(m -> meta(m, true)).messageId(messageId).toolCallId(toolCallId)
            .content(c -> c.set(type))));

        return type == AguiContentType.TEXT ? PayloadMode.TEXT : PayloadMode.RAW;
    }

    private PayloadMode prepareActivity(
        AguiEventType type)
    {
        final String messageId = required("messageId");
        final String activityType = required("activityType");
        final boolean snapshot = type == AguiEventType.ACTIVITY_SNAPSHOT;
        final Member replace = member("replace");
        final int replaced = replace != null && eventBuffer.getByte(replace.valueStart()) == 'f' ? 0 : 1;

        built(ex().activity(a -> a.meta(m -> meta(m, true))
            .type(t -> t.set(snapshot ? AguiActivityType.SNAPSHOT : AguiActivityType.DELTA))
            .messageId(messageId)
            .activityType(activityType)
            .replace(replaced)));

        return PayloadMode.RAW;
    }

    private PayloadMode prepareStep(
        AguiEventType type)
    {
        final String stepName = required("stepName");
        final boolean started = type == AguiEventType.STEP_STARTED;

        if (started && !steps.add(stepName))
        {
            sequence("Step already started: " + stepName);
        }
        else if (!started && !steps.remove(stepName))
        {
            sequence("Step not started: " + stepName);
        }

        built(ex().step(s -> s.meta(m -> meta(m, true)).type(t -> t.set(started ? AguiStepType.STARTED : AguiStepType.FINISHED))
            .stepName(stepName)));

        return PayloadMode.NONE;
    }

    private PayloadMode prepareSubagent(
        AguiEventType type)
    {
        final String subagentRunId = required("subagentRunId");

        switch (type)
        {
        case SUBAGENT_STARTED:
            if (!subagents.add(subagentRunId))
            {
                sequence("Subagent already started: " + subagentRunId);
            }
            break;
        default:
            if (!subagents.remove(subagentRunId))
            {
                sequence("Subagent not started: " + subagentRunId);
            }
            break;
        }

        final AguiSubagentType subagentType = type == AguiEventType.SUBAGENT_STARTED ? AguiSubagentType.STARTED
            : type == AguiEventType.SUBAGENT_FINISHED ? AguiSubagentType.FINISHED : AguiSubagentType.ERROR;
        final String name = optional("name");
        final String description = optional("description");
        final String parentSubagentRunId = optional("parentSubagentRunId");
        final String parentToolCallId = optional("parentToolCallId");
        final String parentMessageId = optional("parentMessageId");
        final String message = type == AguiEventType.SUBAGENT_ERROR ? required("message") : null;
        final String code = type == AguiEventType.SUBAGENT_ERROR ? optional("code") : null;
        final List<String> interruptIds = new ArrayList<>();
        final AguiSubagentOutcome outcome = type == AguiEventType.SUBAGENT_FINISHED
            ? subagentOutcome(interruptIds)
            : AguiSubagentOutcome.NONE;

        built(ex().subagent(s ->
        {
            s.meta(m -> meta(m, false))
                .type(t -> t.set(subagentType))
                .subagentRunId(subagentRunId)
                .name(name)
                .description(description)
                .parentSubagentRunId(parentSubagentRunId)
                .parentToolCallId(parentToolCallId)
                .parentMessageId(parentMessageId)
                .code(code)
                .message(message)
                .outcome(o -> o.set(outcome));
            interruptIds.forEach(id -> s.interruptIdsItem(i -> i.set(id, UTF_8)));
        }));

        return type == AguiEventType.SUBAGENT_FINISHED && member("result") != null ? PayloadMode.RAW : PayloadMode.NONE;
    }

    private AguiSubagentOutcome subagentOutcome(
        List<String> interruptIds)
    {
        final Member member = member(MEMBER_OUTCOME);
        AguiSubagentOutcome outcome = AguiSubagentOutcome.NONE;

        if (member != null && member.valueEnd() >= 0)
        {
            final JsonObject object = readObject().getJsonObject(MEMBER_OUTCOME);
            final JsonValue typeValue = object != null ? object.get(MEMBER_TYPE) : null;
            final String type = typeValue instanceof JsonString string ? string.getString() : null;

            switch (type != null ? type.toLowerCase(Locale.ROOT) : "")
            {
            case "success":
                outcome = AguiSubagentOutcome.SUCCESS;
                break;
            case "suspended":
                outcome = AguiSubagentOutcome.SUSPENDED;
                if (object.containsKey("interruptIds"))
                {
                    object.getJsonArray("interruptIds").forEach(id -> interruptIds.add(((JsonString) id).getString()));
                }
                break;
            default:
                throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported outcome type: " + type);
            }
        }

        return outcome;
    }

    private AguiDataExFW.Builder textEx()
    {
        final String id = required("messageId");
        final String role = optional("role");
        final AguiRole resolved = role != null ? ROLES.get(role) : AguiRole.ASSISTANT;

        if (resolved == null)
        {
            throw new Rejection(CODE_INVALID_MESSAGE, "Unsupported role: " + role);
        }

        return ex().text(t -> t.meta(m -> meta(m, true)).messageId(id).role(r -> r.set(resolved)));
    }

    private AguiDataExFW.Builder toolCallEx()
    {
        final String id = required("toolCallId");
        final String name = required("toolCallName");
        final String parentMessageId = optional("parentMessageId");

        return ex().toolCall(t -> t.meta(m -> meta(m, true)).toolCallId(id).toolCallName(name).parentMessageId(parentMessageId));
    }
}
