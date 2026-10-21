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
    private static final String DIALECT = "anthropic";

    private final String type;

    private String toolCallId;
    private String toolName;
    private boolean opened;
    private boolean argumented;

    LlmAnthropicToolCallFromCanonical(
        String type,
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect);
        this.type = type;
    }

    @Override
    public void reset()
    {
        super.reset();
        toolCallId = null;
        toolName = null;
        opened = false;
        argumented = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "arguments"))
        {
            target = "input";
            argumented = true;
            action = PASS_OBJECT;
        }
        else
        {
            action = unprefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        opened = true;
        toolCallId = attribute("tool-call-id");
        toolName = attribute("tool-name");

        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, type);

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
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (!argumented)
        {
            argumented = true;

            emit(JsonEvent.KEY_NAME, "input");
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onEndObject()
    {
        Status status = Status.REJECTED;

        if (opened)
        {
            if (toolCallId == null)
            {
                toolCallId = attribute("tool-call-id");

                if (toolCallId != null)
                {
                    emit(JsonEvent.KEY_NAME, "id");
                    emit(JsonEvent.VALUE_STRING, toolCallId);
                }
            }

            if (toolName == null)
            {
                toolName = attribute("tool-name");

                if (toolName != null)
                {
                    emit(JsonEvent.KEY_NAME, "name");
                    emit(JsonEvent.VALUE_STRING, toolName);
                }
            }

            if (toolCallId != null && toolName != null)
            {
                emit(JsonEvent.END_OBJECT);
                status = Status.ADVANCED;
            }
        }

        return status;
    }
}
