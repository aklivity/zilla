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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmAnthropicToolCallFromCanonical extends LlmToolTransform
{
    private String toolCallId;
    private String toolName;

    LlmAnthropicToolCallFromCanonical()
    {
        super(MODE_VALUE);
    }

    @Override
    public void reset()
    {
        super.reset();
        toolCallId = null;
        toolName = null;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        return DROP;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        return Status.ADVANCED;
    }

    @Override
    protected void onBegin()
    {
        toolCallId = attribute("tool-call-id");
        toolName = attribute("tool-name");

        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, "tool_use");

        if (toolCallId != null)
        {
            emit(JsonEvent.KEY_NAME, "id");
            emit(JsonEvent.VALUE_STRING, toolCallId);
        }

        if (toolName != null)
        {
            emit(JsonEvent.KEY_NAME, "name");
            emit(JsonEvent.VALUE_STRING, toolName);
        }

        emit(JsonEvent.KEY_NAME, "input");
    }

    @Override
    protected void onValueEnd()
    {
        if (toolCallId != null && toolName != null)
        {
            emit(JsonEvent.END_OBJECT);
        }
    }

    @Override
    protected Status onEnd()
    {
        final String id = toolCallId != null ? toolCallId : attribute("tool-call-id");
        final String name = toolName != null ? toolName : attribute("tool-name");

        if (toolCallId == null && id != null)
        {
            emit(JsonEvent.KEY_NAME, "id");
            emit(JsonEvent.VALUE_STRING, id);
        }

        if (toolName == null && name != null)
        {
            emit(JsonEvent.KEY_NAME, "name");
            emit(JsonEvent.VALUE_STRING, name);
        }

        if (toolCallId == null || toolName == null)
        {
            emit(JsonEvent.END_OBJECT);
        }

        return id != null && name != null ? Status.ADVANCED : Status.REJECTED;
    }
}
