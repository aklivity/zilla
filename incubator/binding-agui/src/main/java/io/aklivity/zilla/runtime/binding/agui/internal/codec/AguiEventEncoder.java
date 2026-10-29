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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.runtime.binding.agui.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiActivityDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiActivityType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiContentType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiCustomDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEndExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiEventMetaFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiOutcomeType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiRawDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiReasoningDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiReasoningEncryptedDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStateDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStepDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiStepType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentOutcome;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiSubagentType;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiTextDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiToolCallDataExFW;
import io.aklivity.zilla.runtime.binding.agui.internal.types.stream.AguiToolResultDataExFW;

public final class AguiEventEncoder
{
    private final String threadId;
    private final String runId;
    private final String parentRunId;
    private final Set<String> steps;
    private final Set<String> subagents;

    private String span;
    private String spanAttribution;

    public AguiEventEncoder(
        String threadId,
        String runId,
        String parentRunId)
    {
        this.threadId = threadId;
        this.runId = runId;
        this.parentRunId = parentRunId;
        this.steps = new HashSet<>();
        this.subagents = new HashSet<>();
    }

    public byte[][] runStarted()
    {
        final StringBuilder event = new StringBuilder("{\"type\":\"RUN_STARTED\"");
        member(event, "threadId", threadId);
        member(event, "runId", runId);
        if (parentRunId != null)
        {
            member(event, "parentRunId", parentRunId);
        }

        return AguiMessageEncoding.events(List.of(event.append('}').toString()));
    }

    public byte[][] runFinished(
        AguiEndExFW endEx)
    {
        if (!steps.isEmpty())
        {
            throw new AguiSequenceException("Run finished with unfinished steps");
        }

        if (!subagents.isEmpty())
        {
            throw new AguiSequenceException("Run finished with unfinished subagents");
        }

        final List<String> events = new ArrayList<>();
        closeSpan(events);

        final StringBuilder event = new StringBuilder("{\"type\":\"RUN_FINISHED\"");
        member(event, "threadId", threadId);
        member(event, "runId", runId);
        finishedDetail(event, endEx);
        events.add(event.append('}').toString());

        return AguiMessageEncoding.events(events);
    }

    public byte[][] runError(
        String message,
        String code)
    {
        final StringBuilder event = new StringBuilder("{\"type\":\"RUN_ERROR\"");
        member(event, "message", message);
        if (code != null)
        {
            member(event, "code", code);
        }

        return AguiMessageEncoding.events(List.of(event.append('}').toString()));
    }

    public AguiMessageEncoding message(
        AguiDataExFW dataEx,
        boolean payloadAbsent)
    {
        final List<String> open = new ArrayList<>();
        final AguiMessageEncoding encoding;

        if (dataEx.kind() != AguiDataExFW.KIND_REASONING && dataEx.kind() != AguiDataExFW.KIND_REASONING_ENCRYPTED)
        {
            closeSpan(open);
        }

        switch (dataEx.kind())
        {
        case AguiDataExFW.KIND_TEXT:
            encoding = text(dataEx.text(), open);
            break;
        case AguiDataExFW.KIND_REASONING:
            encoding = reasoning(dataEx.reasoning(), open);
            break;
        case AguiDataExFW.KIND_REASONING_ENCRYPTED:
            encoding = reasoningEncrypted(dataEx.reasoningEncrypted());
            break;
        case AguiDataExFW.KIND_TOOL_CALL:
            encoding = toolCall(dataEx.toolCall(), open);
            break;
        case AguiDataExFW.KIND_TOOL_RESULT:
            encoding = toolResult(dataEx.toolResult(), open);
            break;
        case AguiDataExFW.KIND_STATE:
            encoding = state(dataEx.state(), open);
            break;
        case AguiDataExFW.KIND_ACTIVITY:
            encoding = activity(dataEx.activity(), open);
            break;
        case AguiDataExFW.KIND_STEP:
            encoding = step(dataEx.step(), open);
            break;
        case AguiDataExFW.KIND_SUBAGENT:
            encoding = subagent(dataEx.subagent(), open, payloadAbsent);
            break;
        case AguiDataExFW.KIND_RAW:
            encoding = raw(dataEx.raw(), open);
            break;
        case AguiDataExFW.KIND_CUSTOM:
            encoding = custom(dataEx.custom(), open);
            break;
        default:
            throw new AguiSequenceException("Unsupported message kind " + dataEx.kind());
        }

        return encoding;
    }

    private AguiMessageEncoding text(
        AguiTextDataExFW text,
        List<String> open)
    {
        final Meta meta = meta(text.meta());
        final String messageId = text.messageId().asString();

        open.add(event("TEXT_MESSAGE_START", meta, true,
            member("messageId", messageId) + member("role", text.role().get().name().toLowerCase(Locale.ROOT))));

        return new AguiMessageEncoding(AguiContentMode.DELTA_EVENTS, open, List.of(),
            "{\"type\":\"TEXT_MESSAGE_CONTENT\"" + member("messageId", messageId) + ",\"delta\":\"",
            "\"" + meta.attribution() + "}",
            List.of(),
            List.of(event("TEXT_MESSAGE_END", meta, false, member("messageId", messageId))));
    }

    private AguiMessageEncoding reasoning(
        AguiReasoningDataExFW reasoning,
        List<String> open)
    {
        final Meta meta = meta(reasoning.meta());
        final String messageId = reasoning.messageId().asString();
        final String groupId = reasoning.groupId().asString() != null ? reasoning.groupId().asString() : messageId;

        if (span != null && !span.equals(groupId))
        {
            closeSpan(open);
        }

        if (span == null)
        {
            open.add("{\"type\":\"REASONING_START\"" + member("messageId", groupId) + meta.attribution() + "}");
            span = groupId;
            spanAttribution = meta.attribution();
        }

        return new AguiMessageEncoding(AguiContentMode.DELTA_EVENTS, open,
            List.of(event("REASONING_MESSAGE_START", meta, true, member("messageId", messageId) + member("role", "reasoning"))),
            "{\"type\":\"REASONING_MESSAGE_CONTENT\"" + member("messageId", messageId) + ",\"delta\":\"",
            "\"" + meta.attribution() + "}",
            List.of(event("REASONING_MESSAGE_END", meta, false, member("messageId", messageId))),
            List.of());
    }

    private AguiMessageEncoding reasoningEncrypted(
        AguiReasoningEncryptedDataExFW encrypted)
    {
        final Meta meta = meta(encrypted.meta());
        final String subtype = encrypted.subtype().get().name().toLowerCase(Locale.ROOT).replace('_', '-');

        return new AguiMessageEncoding(AguiContentMode.STRING_MEMBER, List.of(), List.of(),
            "{\"type\":\"REASONING_ENCRYPTED_VALUE\"" + member("subtype", subtype) +
                member("entityId", encrypted.entityId().asString()) + ",\"encryptedValue\":\"",
            "\"" + meta.tail() + "}",
            List.of(), List.of());
    }

    private AguiMessageEncoding toolCall(
        AguiToolCallDataExFW toolCall,
        List<String> open)
    {
        final Meta meta = meta(toolCall.meta());
        final String toolCallId = toolCall.toolCallId().asString();
        final String parentMessageId = toolCall.parentMessageId().asString();

        open.add(event("TOOL_CALL_START", meta, true,
            member("toolCallId", toolCallId) + member("toolCallName", toolCall.toolCallName().asString()) +
            (parentMessageId != null ? member("parentMessageId", parentMessageId) : "")));

        return new AguiMessageEncoding(AguiContentMode.DELTA_EVENTS, open, List.of(),
            "{\"type\":\"TOOL_CALL_ARGS\"" + member("toolCallId", toolCallId) + ",\"delta\":\"",
            "\"" + meta.attribution() + "}",
            List.of(),
            List.of(event("TOOL_CALL_END", meta, false, member("toolCallId", toolCallId))));
    }

    private AguiMessageEncoding toolResult(
        AguiToolResultDataExFW toolResult,
        List<String> open)
    {
        final Meta meta = meta(toolResult.meta());
        final boolean parts = toolResult.content().get() == AguiContentType.PARTS;

        return new AguiMessageEncoding(parts ? AguiContentMode.JSON_MEMBER : AguiContentMode.STRING_MEMBER, open, List.of(),
            "{\"type\":\"TOOL_CALL_RESULT\"" + member("messageId", toolResult.messageId().asString()) +
                member("toolCallId", toolResult.toolCallId().asString()) + (parts ? ",\"content\":" : ",\"content\":\""),
            (parts ? "" : "\"") + meta.tail() + "}",
            List.of(), List.of());
    }

    private AguiMessageEncoding state(
        AguiStateDataExFW state,
        List<String> open)
    {
        final Meta meta = meta(state.meta());
        final String member;

        switch (state.type().get())
        {
        case SNAPSHOT:
            member = "{\"type\":\"STATE_SNAPSHOT\",\"snapshot\":";
            break;
        case DELTA:
            member = "{\"type\":\"STATE_DELTA\",\"delta\":";
            break;
        default:
            member = "{\"type\":\"MESSAGES_SNAPSHOT\",\"messages\":";
            break;
        }

        return new AguiMessageEncoding(AguiContentMode.JSON_MEMBER, open, List.of(), member, meta.tail() + "}",
            List.of(), List.of());
    }

    private AguiMessageEncoding activity(
        AguiActivityDataExFW activity,
        List<String> open)
    {
        final Meta meta = meta(activity.meta());
        final boolean snapshot = activity.type().get() == AguiActivityType.SNAPSHOT;

        return new AguiMessageEncoding(AguiContentMode.JSON_MEMBER, open, List.of(),
            "{\"type\":\"" + (snapshot ? "ACTIVITY_SNAPSHOT" : "ACTIVITY_DELTA") + "\"" +
                member("messageId", activity.messageId().asString()) +
                member("activityType", activity.activityType().asString()) +
                (snapshot ? ",\"content\":" : ",\"patch\":"),
            (snapshot && activity.replace() == 0 ? ",\"replace\":false" : "") + meta.tail() + "}",
            List.of(), List.of());
    }

    private AguiMessageEncoding step(
        AguiStepDataExFW step,
        List<String> open)
    {
        final Meta meta = meta(step.meta());
        final String stepName = step.stepName().asString();
        final boolean started = step.type().get() == AguiStepType.STARTED;

        if (started && !steps.add(stepName))
        {
            throw new AguiSequenceException("Step already started: " + stepName);
        }
        else if (!started && !steps.remove(stepName))
        {
            throw new AguiSequenceException("Step not started: " + stepName);
        }

        open.add(event(started ? "STEP_STARTED" : "STEP_FINISHED", meta, true, member("stepName", stepName)));

        return new AguiMessageEncoding(AguiContentMode.NONE, open, List.of(), null, null, List.of(), List.of());
    }

    private AguiMessageEncoding subagent(
        AguiSubagentDataExFW subagent,
        List<String> open,
        boolean payloadAbsent)
    {
        final Meta meta = meta(subagent.meta()).withoutAttribution();
        final String subagentRunId = subagent.subagentRunId().asString();
        final AguiSubagentType type = subagent.type().get();
        final AguiMessageEncoding encoding;

        switch (type)
        {
        case STARTED:
            if (!subagents.add(subagentRunId))
            {
                throw new AguiSequenceException("Subagent already started: " + subagentRunId);
            }

            open.add(event("SUBAGENT_STARTED", meta, true,
                member("subagentRunId", subagentRunId) +
                optional("name", subagent.name().asString()) +
                optional("description", subagent.description().asString()) +
                optional("parentSubagentRunId", subagent.parentSubagentRunId().asString()) +
                optional("parentToolCallId", subagent.parentToolCallId().asString()) +
                optional("parentMessageId", subagent.parentMessageId().asString())));
            encoding = new AguiMessageEncoding(AguiContentMode.NONE, open, List.of(), null, null, List.of(), List.of());
            break;
        case FINISHED:
            if (!subagents.remove(subagentRunId))
            {
                throw new AguiSequenceException("Subagent not started: " + subagentRunId);
            }

            final String outcome = subagentOutcome(subagent.outcome().get());

            if (payloadAbsent)
            {
                open.add(event("SUBAGENT_FINISHED", meta, true, member("subagentRunId", subagentRunId) + outcome));
                encoding = new AguiMessageEncoding(AguiContentMode.NONE, open, List.of(), null, null, List.of(), List.of());
            }
            else
            {
                encoding = new AguiMessageEncoding(AguiContentMode.JSON_MEMBER, open, List.of(),
                    "{\"type\":\"SUBAGENT_FINISHED\"" + member("subagentRunId", subagentRunId) + ",\"result\":",
                    outcome + meta.tail() + "}",
                    List.of(), List.of());
            }
            break;
        default:
            if (!subagents.remove(subagentRunId))
            {
                throw new AguiSequenceException("Subagent not started: " + subagentRunId);
            }

            open.add(event("SUBAGENT_ERROR", meta, true,
                member("subagentRunId", subagentRunId) + member("message", subagent.message().asString()) +
                optional("code", subagent.code().asString())));
            encoding = new AguiMessageEncoding(AguiContentMode.NONE, open, List.of(), null, null, List.of(), List.of());
            break;
        }

        return encoding;
    }

    private AguiMessageEncoding raw(
        AguiRawDataExFW raw,
        List<String> open)
    {
        final Meta meta = meta(raw.meta());

        return new AguiMessageEncoding(AguiContentMode.JSON_MEMBER, open, List.of(),
            "{\"type\":\"RAW\",\"event\":",
            optional("source", raw.source().asString()) + meta.tail() + "}",
            List.of(), List.of());
    }

    private AguiMessageEncoding custom(
        AguiCustomDataExFW custom,
        List<String> open)
    {
        final Meta meta = meta(custom.meta());

        return new AguiMessageEncoding(AguiContentMode.JSON_MEMBER, open, List.of(),
            "{\"type\":\"CUSTOM\"" + member("name", custom.name().asString()) + ",\"value\":",
            meta.tail() + "}",
            List.of(), List.of());
    }

    private void closeSpan(
        List<String> events)
    {
        if (span != null)
        {
            events.add("{\"type\":\"REASONING_END\"" + member("messageId", span) + spanAttribution + "}");
            span = null;
            spanAttribution = null;
        }
    }

    private void finishedDetail(
        StringBuilder event,
        AguiEndExFW endEx)
    {
        final AguiOutcomeType outcomeType = endEx != null ? endEx.outcome().get() : AguiOutcomeType.NONE;
        final JsonObject detail = detail(endEx != null ? endEx.detail() : null);

        boolean outcomeWritten = false;

        for (var entry : detail.entrySet())
        {
            if ("outcome".equals(entry.getKey()) && outcomeType != AguiOutcomeType.NONE)
            {
                event.append(",\"outcome\":").append(outcome(outcomeType, entry.getValue()));
                outcomeWritten = true;
            }
            else
            {
                event.append(",\"").append(AguiJsonEscape.escape(entry.getKey())).append("\":").append(entry.getValue());
            }
        }

        if (outcomeType != AguiOutcomeType.NONE && !outcomeWritten)
        {
            event.append(",\"outcome\":").append(outcome(outcomeType, null));
        }
    }

    private static String outcome(
        AguiOutcomeType outcomeType,
        JsonValue members)
    {
        final JsonObjectBuilder outcome = Json.createObjectBuilder()
            .add("type", outcomeType.name().toLowerCase(Locale.ROOT));

        if (members instanceof JsonObject object)
        {
            object.forEach(outcome::add);
        }

        return outcome.build().toString();
    }

    private static JsonObject detail(
        OctetsFW detail)
    {
        JsonObject object = JsonObject.EMPTY_JSON_OBJECT;

        if (detail != null && detail.sizeof() > 0)
        {
            final String text = detail.get((buffer, offset, limit) -> buffer.getStringWithoutLengthUtf8(offset, limit - offset));

            try
            {
                object = Json.createReader(new StringReader(text)).readObject();
            }
            catch (JsonException | IllegalStateException ex)
            {
                throw new AguiSequenceException("Run finished detail is not a JSON object");
            }
        }

        return object;
    }

    private static String subagentOutcome(
        AguiSubagentOutcome outcome)
    {
        final String text;

        switch (outcome)
        {
        case SUCCESS:
            text = ",\"outcome\":{\"type\":\"success\"}";
            break;
        case SUSPENDED:
            text = ",\"outcome\":{\"type\":\"suspended\"}";
            break;
        default:
            text = "";
            break;
        }

        return text;
    }

    private static String event(
        String type,
        Meta meta,
        boolean first,
        String members)
    {
        return "{\"type\":\"" + type + "\"" + members + (first ? meta.tail() : meta.attribution()) + "}";
    }

    private static String member(
        String name,
        String value)
    {
        return ",\"" + name + "\":\"" + AguiJsonEscape.escape(value) + "\"";
    }

    private static void member(
        StringBuilder event,
        String name,
        String value)
    {
        event.append(member(name, value));
    }

    private static String optional(
        String name,
        String value)
    {
        return value != null ? member(name, value) : "";
    }

    private static Meta meta(
        AguiEventMetaFW meta)
    {
        final OctetsFW metadata = meta.metadata();

        return new Meta(meta.timestamp(), meta.subagentRunId().asString(),
            metadata != null && metadata.sizeof() > 0
                ? metadata.get((buffer, offset, limit) -> buffer.getStringWithoutLengthUtf8(offset, limit - offset))
                : null);
    }

    private record Meta(
        long timestamp,
        String subagent,
        String metadata)
    {
        String attribution()
        {
            return subagent != null ? member("subagentRunId", subagent) : "";
        }

        String tail()
        {
            return (timestamp >= 0 ? ",\"timestamp\":" + timestamp : "") +
                attribution() +
                (metadata != null ? ",\"metadata\":" + metadata : "");
        }

        Meta withoutAttribution()
        {
            return new Meta(timestamp, null, metadata);
        }
    }
}
