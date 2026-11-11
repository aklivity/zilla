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

final class LlmOpenaiResponsesToolCallFromCanonical extends LlmToolTransform
{
    private static final int BIT_EMIT_KEY_NAME = 1 << 0;
    private static final int BIT_EMIT_VALUE_STRING = 1 << 1;
    private static final int BIT_EMIT_KEY_NAME_2 = 1 << 2;
    private static final int BIT_EMIT_VALUE_STRING_2 = 1 << 3;
    private static final int BIT_EMIT_KEY_NAME_3 = 1 << 4;
    private static final int BIT_EMIT_VALUE_STRING_3 = 1 << 5;
    private static final int BIT_EMIT_KEY_NAME_4 = 1 << 6;
    private static final int BIT_EMIT_VALUE_STRING_4 = 1 << 7;
    private static final int BIT_EMIT_END_OBJECT = 1 << 8;


    private boolean opened;
    private boolean argumented;

    LlmOpenaiResponsesToolCallFromCanonical(
        String dialect)
    {
        super(MODE_OBJECT, dialect);
    }

    @Override
    public void reset()
    {
        super.reset();
        opened = false;
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
            target = "arguments";
            argumented = true;
            action = ESCAPE_OBJECT;
        }
        else
        {
            action = unprefix(key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        opened = true;

        emit(BIT_EMIT_KEY_NAME, JsonEvent.KEY_NAME, "type");
        emit(BIT_EMIT_VALUE_STRING, JsonEvent.VALUE_STRING, "function_call");
    }

    @Override
    protected Status onClose(
        int depth)
    {
        return Status.ADVANCED;
    }

    @Override
    protected Status onEndObject()
    {
        Status status = Status.REJECTED;

        if (opened)
        {
            final String toolCallId = attribute("tool-call-id");
            final String toolName = attribute("tool-name");

            if (toolCallId != null && toolName != null)
            {
                if (!argumented)
                {
                    emit(BIT_EMIT_KEY_NAME_2, JsonEvent.KEY_NAME, "arguments");
                    emit(BIT_EMIT_VALUE_STRING_2, JsonEvent.VALUE_STRING, "{}");
                }

                emit(BIT_EMIT_KEY_NAME_3, JsonEvent.KEY_NAME, "call_id");
                emit(BIT_EMIT_VALUE_STRING_3, JsonEvent.VALUE_STRING, toolCallId);
                emit(BIT_EMIT_KEY_NAME_4, JsonEvent.KEY_NAME, "name");
                emit(BIT_EMIT_VALUE_STRING_4, JsonEvent.VALUE_STRING, toolName);
                emit(BIT_EMIT_END_OBJECT, JsonEvent.END_OBJECT);
                status = Status.ADVANCED;
            }
        }

        return status;
    }
}
