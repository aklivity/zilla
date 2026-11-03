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

final class LlmGeminiServerToolCallFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";
    private static final String MEMBER_WIRE_ID = "gemini:id";

    private boolean opened;
    private boolean called;

    LlmGeminiServerToolCallFromCanonical(
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
    }

    @Override
    public void reset()
    {
        super.reset();
        opened = false;
        called = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, "arguments"))
        {
            action = DESCEND;
        }
        else if (depth == 2)
        {
            target = key.toString();
            action = PASS_VALUE;
        }
        else if (depth == 1 && matches(key, MEMBER_WIRE_ID))
        {
            action = HOLD_STRING;
        }
        else
        {
            endCall();

            action = contains(key, ':') ? unprefix(key) : DROP;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        opened = true;

        emit(JsonEvent.KEY_NAME, "executableCode");
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
            endCall();
            emit(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private void endCall()
    {
        if (opened && !called)
        {
            called = true;

            if (held() != null)
            {
                emit(JsonEvent.KEY_NAME, "id");
                emit(JsonEvent.VALUE_STRING, held());
            }

            emit(JsonEvent.END_OBJECT);
        }
    }

    private static boolean contains(
        CharSequence key,
        char value)
    {
        boolean found = false;

        for (int index = 0; !found && index < key.length(); index++)
        {
            found = key.charAt(index) == value;
        }

        return found;
    }
}
