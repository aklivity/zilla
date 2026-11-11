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
    private static final int BIT_ID_KEY = 1 << 0;
    private static final int BIT_ID_VALUE = 1 << 1;
    private static final int BIT_TYPE_KEY = 1 << 2;
    private static final int BIT_TYPE_VALUE = 1 << 3;
    private static final int BIT_FUNCTION_KEY = 1 << 4;
    private static final int BIT_FUNCTION_START = 1 << 5;
    private static final int BIT_FUNCTION_NAME_KEY = 1 << 6;
    private static final int BIT_FUNCTION_NAME_VALUE = 1 << 7;
    private static final int BIT_ARGUMENTS_KEY = 1 << 8;
    private static final int BIT_ARGUMENTS_VALUE = 1 << 9;
    private static final int BIT_LATE_NAME_KEY = 1 << 10;
    private static final int BIT_LATE_NAME_VALUE = 1 << 11;
    private static final int BIT_FUNCTION_END = 1 << 12;
    private static final int BIT_END_OBJECT = 1 << 13;

    private String toolCallId;
    private String toolName;
    private boolean opened;
    private boolean functioned;
    private boolean argumented;

    LlmOpenaiToolCallFromCanonical(
        String dialect)
    {
        super(MODE_OBJECT, dialect);
    }

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
    protected boolean resumable()
    {
        return true;
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
            action = unprefix(key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        final String openId = attribute("tool-call-id");
        final String openName = attribute("tool-name");

        if (emitId(openId) &&
            emit(BIT_TYPE_KEY, JsonEvent.KEY_NAME, "type") &&
            emit(BIT_TYPE_VALUE, JsonEvent.VALUE_STRING, "function"))
        {
            opened = true;
            toolCallId = openId;
            toolName = openName;
        }
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
            final boolean closing = functioned || !argumented;
            final String lateName = toolName == null && closing ? attribute("tool-name") : null;
            final String lateId = toolCallId == null ? attribute("tool-call-id") : null;

            final boolean closed =
                (functioned || argumented || emitFunctionOpen(toolName)) &&
                (argumented || emitEmptyArguments()) &&
                (!closing || emitFunctionClose(lateName)) &&
                emitId(lateId);

            if (closed && (toolCallId != null || lateId != null) && (toolName != null || lateName != null) &&
                emit(BIT_END_OBJECT, JsonEvent.END_OBJECT))
            {
                status = Status.ADVANCED;
            }
        }

        return status;
    }

    private void openFunction()
    {
        if (!functioned && emitFunctionOpen(toolName))
        {
            functioned = true;
        }
    }

    private void closeFunction()
    {
        if (functioned)
        {
            final String lateName = toolName == null ? attribute("tool-name") : null;

            if (emitFunctionClose(lateName))
            {
                functioned = false;

                if (lateName != null)
                {
                    toolName = lateName;
                }
            }
        }
    }

    private boolean emitId(
        String id)
    {
        return id == null ||
            emit(BIT_ID_KEY, JsonEvent.KEY_NAME, "id") &&
            emit(BIT_ID_VALUE, JsonEvent.VALUE_STRING, id);
    }

    private boolean emitEmptyArguments()
    {
        return emit(BIT_ARGUMENTS_KEY, JsonEvent.KEY_NAME, "arguments") &&
            emit(BIT_ARGUMENTS_VALUE, JsonEvent.VALUE_STRING, "{}");
    }

    private boolean emitFunctionOpen(
        String name)
    {
        return emit(BIT_FUNCTION_KEY, JsonEvent.KEY_NAME, "function") &&
            emit(BIT_FUNCTION_START, JsonEvent.START_OBJECT) &&
            (name == null ||
                emit(BIT_FUNCTION_NAME_KEY, JsonEvent.KEY_NAME, "name") &&
                emit(BIT_FUNCTION_NAME_VALUE, JsonEvent.VALUE_STRING, name));
    }

    private boolean emitFunctionClose(
        String lateName)
    {
        return (lateName == null ||
            emit(BIT_LATE_NAME_KEY, JsonEvent.KEY_NAME, "name") &&
            emit(BIT_LATE_NAME_VALUE, JsonEvent.VALUE_STRING, lateName)) &&
            emit(BIT_FUNCTION_END, JsonEvent.END_OBJECT);
    }
}
