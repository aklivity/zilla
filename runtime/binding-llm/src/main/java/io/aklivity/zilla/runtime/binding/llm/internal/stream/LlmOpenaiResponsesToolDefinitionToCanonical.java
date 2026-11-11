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

final class LlmOpenaiResponsesToolDefinitionToCanonical extends LlmToolTransform
{
    private static final String DIALECT = LlmOpenaiResponsesDialectFactorySpi.NAME;
    private static final String TYPE_FUNCTION = "function";
    private static final int BIT_EMIT_KEY_NAME = 1 << 0;
    private static final int BIT_EMIT_VALUE_STRING = 1 << 1;
    private static final int BIT_EMIT_KEY_NAME_2 = 1 << 2;
    private static final int BIT_EMIT_START_OBJECT = 1 << 3;
    private static final int BIT_EMIT_END_OBJECT = 1 << 4;
    private static final int BIT_PASS_END_OBJECT = 1 << 5;

    private boolean named;

    @Override
    public void reset()
    {
        super.reset();
        named = false;
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

        if (matches(key, "name"))
        {
            target = "name";
            named = true;
            action = PASS_STRING;
        }
        else if (matches(key, "description"))
        {
            target = "description";
            action = PASS_STRING_OR_DROP;
        }
        else if (matches(key, "parameters"))
        {
            target = "parameters";
            action = PASS_VALUE;
        }
        else if (matches(key, "strict"))
        {
            target = "strict";
            action = PASS_VALUE;
        }
        else if (matches(key, "type"))
        {
            action = HOLD_STRING;
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        emit(BIT_EMIT_KEY_NAME, JsonEvent.KEY_NAME, "type");
        emit(BIT_EMIT_VALUE_STRING, JsonEvent.VALUE_STRING, TYPE_FUNCTION);
        emit(BIT_EMIT_KEY_NAME_2, JsonEvent.KEY_NAME, "function");
        emit(BIT_EMIT_START_OBJECT, JsonEvent.START_OBJECT);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.REJECTED;

        if (named && TYPE_FUNCTION.equals(held()))
        {
            emit(BIT_EMIT_END_OBJECT, JsonEvent.END_OBJECT);
            pass(BIT_PASS_END_OBJECT, JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }
}
