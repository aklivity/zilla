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

final class LlmOpenaiToolCallFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";

    private String toolCallId;
    private String toolName;
    private boolean opened;
    private boolean functioned;
    private boolean argumented;

    @Override
    public void reset()
    {
        super.reset();
        toolCallId = null;
        toolName = null;
        opened = false;
        functioned = false;
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
            openFunction();
            target = "arguments";
            argumented = true;
            action = ESCAPE_OBJECT;
        }
        else
        {
            closeFunction();
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

        if (toolCallId != null)
        {
            emit(JsonEvent.KEY_NAME, "id");
            emit(JsonEvent.VALUE_STRING, toolCallId);
        }

        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, "function");
    }

    @Override
    protected Status onClose(
        int depth)
    {
        return Status.ADVANCED;
    }

    @Override
    protected void onEscapedEnd()
    {
        if (toolName != null)
        {
            closeFunction();
        }
    }

    @Override
    protected Status onEndObject()
    {
        Status status = Status.REJECTED;

        if (opened)
        {
            if (!argumented)
            {
                openFunction();
                emit(JsonEvent.KEY_NAME, "arguments");
                emit(JsonEvent.VALUE_STRING, "{}");
            }

            closeFunction();

            if (toolCallId == null)
            {
                toolCallId = attribute("tool-call-id");

                if (toolCallId != null)
                {
                    emit(JsonEvent.KEY_NAME, "id");
                    emit(JsonEvent.VALUE_STRING, toolCallId);
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

    private void openFunction()
    {
        if (!functioned)
        {
            functioned = true;

            emit(JsonEvent.KEY_NAME, "function");
            emit(JsonEvent.START_OBJECT);

            if (toolName != null)
            {
                emit(JsonEvent.KEY_NAME, "name");
                emit(JsonEvent.VALUE_STRING, toolName);
            }
        }
    }

    private void closeFunction()
    {
        if (functioned)
        {
            functioned = false;

            if (toolName == null)
            {
                toolName = attribute("tool-name");

                if (toolName != null)
                {
                    emit(JsonEvent.KEY_NAME, "name");
                    emit(JsonEvent.VALUE_STRING, toolName);
                }
            }

            emit(JsonEvent.END_OBJECT);
        }
    }
}
