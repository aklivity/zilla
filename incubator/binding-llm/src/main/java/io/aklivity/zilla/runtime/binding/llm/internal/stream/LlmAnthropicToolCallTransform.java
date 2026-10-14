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

final class LlmAnthropicToolCallTransform extends LlmToolTransform
{
    private boolean identified;
    private boolean named;
    private boolean inputSeen;
    private boolean wrapped;

    @Override
    public void reset()
    {
        super.reset();
        identified = false;
        named = false;
        inputSeen = false;
        wrapped = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (matches(key, "id"))
        {
            target = "id";
            identified = true;
            action = wrapped ? HOLD_STRING : PASS_STRING;
        }
        else if (matches(key, "name"))
        {
            wrap();
            target = "name";
            named = true;
            action = PASS_STRING;
        }
        else if (matches(key, "input"))
        {
            wrap();
            target = "arguments";
            inputSeen = true;
            action = ESCAPE_OBJECT;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.REJECTED;

        if (identified && named)
        {
            if (!inputSeen)
            {
                emit(JsonEvent.KEY_NAME, "arguments");
                emit(JsonEvent.VALUE_STRING, "{}");
            }

            emit(JsonEvent.END_OBJECT);

            if (held() != null)
            {
                emit(JsonEvent.KEY_NAME, "id");
                emit(JsonEvent.VALUE_STRING, held());
            }

            pass(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private void wrap()
    {
        if (!wrapped)
        {
            wrapped = true;
            emit(JsonEvent.KEY_NAME, "type");
            emit(JsonEvent.VALUE_STRING, "function");
            emit(JsonEvent.KEY_NAME, "function");
            emit(JsonEvent.START_OBJECT);
        }
    }
}
