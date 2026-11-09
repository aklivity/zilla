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

enum AguiEventType
{
    RUN_STARTED(null, false),
    RUN_FINISHED(null, false),
    RUN_ERROR(null, false),
    TEXT_MESSAGE_START(null, false),
    TEXT_MESSAGE_CONTENT("delta", false),
    TEXT_MESSAGE_END(null, false),
    REASONING_START(null, false),
    REASONING_MESSAGE_START(null, false),
    REASONING_MESSAGE_CONTENT("delta", false),
    REASONING_MESSAGE_END(null, false),
    REASONING_END(null, false),
    REASONING_ENCRYPTED_VALUE("encryptedValue", true),
    TOOL_CALL_START(null, false),
    TOOL_CALL_ARGS("delta", false),
    TOOL_CALL_END(null, false),
    TOOL_CALL_RESULT("content", true),
    STATE_SNAPSHOT("snapshot", true),
    STATE_DELTA("delta", true),
    MESSAGES_SNAPSHOT("messages", true),
    ACTIVITY_SNAPSHOT("content", true),
    ACTIVITY_DELTA("patch", true),
    STEP_STARTED(null, false),
    STEP_FINISHED(null, false),
    SUBAGENT_STARTED(null, false),
    SUBAGENT_FINISHED("result", true),
    SUBAGENT_ERROR(null, false),
    RAW("event", true),
    CUSTOM("value", true);

    private static final Map<String, AguiEventType> BY_NAME = Map.ofEntries(
        Map.entry("RUN_STARTED", RUN_STARTED),
        Map.entry("RUN_FINISHED", RUN_FINISHED),
        Map.entry("RUN_ERROR", RUN_ERROR),
        Map.entry("TEXT_MESSAGE_START", TEXT_MESSAGE_START),
        Map.entry("TEXT_MESSAGE_CONTENT", TEXT_MESSAGE_CONTENT),
        Map.entry("TEXT_MESSAGE_END", TEXT_MESSAGE_END),
        Map.entry("REASONING_START", REASONING_START),
        Map.entry("REASONING_MESSAGE_START", REASONING_MESSAGE_START),
        Map.entry("REASONING_MESSAGE_CONTENT", REASONING_MESSAGE_CONTENT),
        Map.entry("REASONING_MESSAGE_END", REASONING_MESSAGE_END),
        Map.entry("REASONING_END", REASONING_END),
        Map.entry("REASONING_ENCRYPTED_VALUE", REASONING_ENCRYPTED_VALUE),
        Map.entry("TOOL_CALL_START", TOOL_CALL_START),
        Map.entry("TOOL_CALL_ARGS", TOOL_CALL_ARGS),
        Map.entry("TOOL_CALL_END", TOOL_CALL_END),
        Map.entry("TOOL_CALL_RESULT", TOOL_CALL_RESULT),
        Map.entry("STATE_SNAPSHOT", STATE_SNAPSHOT),
        Map.entry("STATE_DELTA", STATE_DELTA),
        Map.entry("MESSAGES_SNAPSHOT", MESSAGES_SNAPSHOT),
        Map.entry("ACTIVITY_SNAPSHOT", ACTIVITY_SNAPSHOT),
        Map.entry("ACTIVITY_DELTA", ACTIVITY_DELTA),
        Map.entry("STEP_STARTED", STEP_STARTED),
        Map.entry("STEP_FINISHED", STEP_FINISHED),
        Map.entry("SUBAGENT_STARTED", SUBAGENT_STARTED),
        Map.entry("SUBAGENT_FINISHED", SUBAGENT_FINISHED),
        Map.entry("SUBAGENT_ERROR", SUBAGENT_ERROR),
        Map.entry("RAW", RAW),
        Map.entry("CUSTOM", CUSTOM));

    final String payload;
    final boolean streamable;

    AguiEventType(
        String payload,
        boolean streamable)
    {
        this.payload = payload;
        this.streamable = streamable;
    }

    static AguiEventType of(
        String name)
    {
        return BY_NAME.get(name);
    }
}
