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

    public static final Set<String> TAG_MEMBERS = Set.of("event", "type", "name", "messageId", "groupId", "activityType",
        "subtype", "entityId", "executor", "parentMessageId", "source", "replace");
    public static final Set<String> DATA_MEMBERS = Set.of("id", "name", "toolCallId");

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
        Map.entry("subagent", "subagents"),
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

    public boolean clientExecuted()
    {
        return EVENT_TOOL_CALL.equals(event) && !"agent".equals(executor);
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
