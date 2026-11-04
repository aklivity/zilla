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

final class LlmGeminiServerToolUseFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";

    private boolean opened;

    LlmGeminiServerToolUseFromCanonical(
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
    }

    @Override
    public void reset()
    {
        super.reset();
        opened = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, "arguments"))
        {
            target = "args";
            action = PASS_OBJECT;
        }
        else if (depth == 1 && matches(key, "gemini:thoughtSignature"))
        {
            action = HOLD_STRING;
        }
        else
        {
            action = key.toString().indexOf(':') >= 0 ? unprefix(key) : DROP;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        opened = true;

        emit(JsonEvent.KEY_NAME, "toolCall");
        emit(JsonEvent.START_OBJECT);
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
            emit(JsonEvent.END_OBJECT);

            if (held() != null)
            {
                emit(JsonEvent.KEY_NAME, "thoughtSignature");
                emit(JsonEvent.VALUE_STRING, held());
            }

            emit(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }
}
