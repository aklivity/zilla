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
    private static final String DIALECT = "openai";

    private boolean opened;
    private boolean argumented;

    LlmOpenaiResponsesToolCallFromCanonical(
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect);
    }

    @Override
    public void reset()
    {
        super.reset();
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
            target = "arguments";
            argumented = true;
            action = ESCAPE_OBJECT;
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

        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, "function_call");
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
                    emit(JsonEvent.KEY_NAME, "arguments");
                    emit(JsonEvent.VALUE_STRING, "{}");
                }

                emit(JsonEvent.KEY_NAME, "call_id");
                emit(JsonEvent.VALUE_STRING, toolCallId);
                emit(JsonEvent.KEY_NAME, "name");
                emit(JsonEvent.VALUE_STRING, toolName);
                emit(JsonEvent.END_OBJECT);
                status = Status.ADVANCED;
            }
        }

        return status;
    }
}
