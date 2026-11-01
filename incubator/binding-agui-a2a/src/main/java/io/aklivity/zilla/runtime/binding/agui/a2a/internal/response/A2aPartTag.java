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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import java.util.Map;
import java.util.Set;

public final class A2aPartTag
{
    public static final String EVENT_STATE = "state";
    public static final String EVENT_ACTIVITY = "activity";
    public static final String EVENT_REASONING = "reasoning";
    public static final String EVENT_REASONING_ENCRYPTED = "reasoning.encrypted";
    public static final String EVENT_STEP = "step";
    public static final String EVENT_TOOL_CALL = "toolCall";
    public static final String EVENT_TOOL_RESULT = "toolResult";
    public static final String EVENT_CUSTOM = "custom";
    public static final String EVENT_RAW = "raw";
    public static final String EVENT_SUBAGENT = "subagent";
    public static final String EVENT_TEXT = "text";

    public static final Set<String> TAG_MEMBERS = Set.of("event", "type", "name", "messageId", "groupId", "activityType",
        "subtype", "entityId", "executor", "parentMessageId", "source", "replace", "subagentRunId", "description",
        "parentSubagentRunId", "parentToolCallId", "outcome", "code", "message");
    public static final Set<String> DATA_MEMBERS = Set.of("id", "name", "toolCallId");
    public static final Set<String> OPERATION_KEYS = Set.of("createSurface", "updateComponents", "updateDataModel",
        "deleteSurface", "beginRendering", "surfaceUpdate", "dataModelUpdate");

    private static final String MEDIA_TYPE_A2UI = "application/a2ui+json";
    private static final String MEDIA_TYPE_A2UI_DEPRECATED = "application/json+a2ui";

    private static final Map<String, String> FEATURES = Map.ofEntries(
        Map.entry(EVENT_STATE, "state"),
        Map.entry(EVENT_ACTIVITY, "activity"),
        Map.entry(EVENT_REASONING, "reasoning"),
        Map.entry(EVENT_REASONING_ENCRYPTED, "reasoning"),
        Map.entry(EVENT_STEP, "steps"),
        Map.entry(EVENT_TOOL_CALL, "toolCalls"),
        Map.entry(EVENT_TOOL_RESULT, "toolCalls"),
        Map.entry(EVENT_CUSTOM, "custom"),
        Map.entry(EVENT_RAW, "custom"),
        Map.entry(EVENT_SUBAGENT, "subagents"),
        Map.entry(EVENT_TEXT, "subagents"),
        Map.entry("interrupt", "interrupts"));

    public String event;
    public String type;
    public String name;
    public String messageId;
    public String groupId;
    public String activityType;
    public String subtype;
    public String entityId;
    public String executor;
    public String parentMessageId;
    public String source;
    public String subagentRunId;
    public String description;
    public String parentSubagentRunId;
    public String parentToolCallId;
    public String outcome;
    public String code;
    public String message;
    public String mediaType;
    public String mimeType;
    public String surfaceId;
    public Boolean replace;
    public String callId;
    public String callName;
    public String resultCallId;

    public void clear()
    {
        event = null;
        type = null;
        name = null;
        messageId = null;
        groupId = null;
        activityType = null;
        subtype = null;
        entityId = null;
        executor = null;
        parentMessageId = null;
        source = null;
        subagentRunId = null;
        description = null;
        parentSubagentRunId = null;
        parentToolCallId = null;
        outcome = null;
        code = null;
        message = null;
        mediaType = null;
        mimeType = null;
        surfaceId = null;
        replace = null;
        callId = null;
        callName = null;
        resultCallId = null;
    }

    public void tag(
        String member,
        String value)
    {
        switch (member)
        {
        case "event" -> event = value;
        case "type" -> type = value;
        case "name" -> name = value;
        case "messageId" -> messageId = value;
        case "groupId" -> groupId = value;
        case "activityType" -> activityType = value;
        case "subtype" -> subtype = value;
        case "entityId" -> entityId = value;
        case "executor" -> executor = value;
        case "parentMessageId" -> parentMessageId = value;
        case "source" -> source = value;
        case "subagentRunId" -> subagentRunId = value;
        case "description" -> description = value;
        case "parentSubagentRunId" -> parentSubagentRunId = value;
        case "parentToolCallId" -> parentToolCallId = value;
        case "outcome" -> outcome = value;
        case "code" -> code = value;
        case "message" -> message = value;
        default -> ignore();
        }
    }

    public void data(
        String member,
        String value)
    {
        switch (member)
        {
        case "id" -> callId = value;
        case "name" -> callName = value;
        case "toolCallId" -> resultCallId = value;
        default -> ignore();
        }
    }

    private void ignore()
    {
    }

    public boolean a2uiPart()
    {
        return isA2ui(mediaType) || isA2ui(mimeType);
    }

    private static boolean isA2ui(
        String type)
    {
        return MEDIA_TYPE_A2UI.equals(type) || MEDIA_TYPE_A2UI_DEPRECATED.equals(type);
    }

    public boolean tagged()
    {
        return event != null;
    }

    public String feature()
    {
        return FEATURES.get(event);
    }

    public boolean known()
    {
        return FEATURES.containsKey(event);
    }

    public boolean needsData()
    {
        return EVENT_TOOL_CALL.equals(event) || EVENT_TOOL_RESULT.equals(event);
    }

    public String dataMember()
    {
        return EVENT_TOOL_CALL.equals(event) ? "arguments" : EVENT_TOOL_RESULT.equals(event) ? "payload" : null;
    }

    public boolean agentExecuted()
    {
        return "agent".equals(executor);
    }

    public String problem()
    {
        final String problem;

        switch (event)
        {
        case EVENT_STATE:
            problem = oneOf(type, "snapshot", "delta", "messages") ? null : "state tag needs type";
            break;
        case EVENT_ACTIVITY:
            problem = oneOf(type, "snapshot", "delta") && activityType != null && messageId != null
                ? null : "activity tag needs type, activityType and messageId";
            break;
        case EVENT_REASONING:
            problem = messageId != null ? null : "reasoning tag needs messageId";
            break;
        case EVENT_REASONING_ENCRYPTED:
            problem = oneOf(subtype, "message", "toolCall") && entityId != null
                ? null : "reasoning.encrypted tag needs subtype and entityId";
            break;
        case EVENT_STEP:
            problem = oneOf(type, "started", "finished") && name != null ? null : "step tag needs type and name";
            break;
        case EVENT_TOOL_CALL:
            problem = callId != null && callName != null ? null : "toolCall part needs id and name";
            break;
        case EVENT_TOOL_RESULT:
            problem = resultCallId != null ? null : "toolResult part needs toolCallId";
            break;
        case EVENT_CUSTOM:
            problem = name != null ? null : "custom tag needs name";
            break;
        case EVENT_SUBAGENT:
            problem = oneOf(type, "started", "finished", "error") && subagentRunId != null &&
                (outcome == null || oneOf(outcome, "success", "suspended"))
                ? null : "subagent tag needs type and subagentRunId";
            break;
        case EVENT_TEXT:
            problem = subagentRunId != null ? null : "text tag needs subagentRunId";
            break;
        default:
            problem = null;
            break;
        }

        return problem;
    }

    private static boolean oneOf(
        String value,
        String... options)
    {
        boolean found = false;

        for (String option : options)
        {
            found |= option.equals(value);
        }

        return found;
    }
}
