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

final class LlmAnthropicToolDefinitionFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final int BIT_SCHEMA_KEY = 1 << 0;
    private static final int BIT_SCHEMA_START = 1 << 1;
    private static final int BIT_SCHEMA_TYPE_KEY = 1 << 2;
    private static final int BIT_SCHEMA_TYPE_VALUE = 1 << 3;
    private static final int BIT_SCHEMA_END = 1 << 4;
    private static final int BIT_END_OBJECT = 1 << 5;

    private final LlmCacheBreakpoint breakpoints;

    private boolean named;
    private boolean parametrized;

    LlmAnthropicToolDefinitionFromCanonical(
        boolean sameDialect)
    {
        this(sameDialect, null);
    }

    LlmAnthropicToolDefinitionFromCanonical(
        boolean sameDialect,
        LlmCacheBreakpoint breakpoints)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
        this.breakpoints = breakpoints;
    }

    @Override
    public void reset()
    {
        super.reset();
        named = false;
        parametrized = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, "type"))
        {
            action = DROP;
        }
        else if (depth == 1 && matches(key, "function"))
        {
            action = DESCEND;
        }
        else if (depth == 2 && matches(key, "name"))
        {
            target = "name";
            named = true;
            action = PASS_STRING;
        }
        else if (depth == 2 && matches(key, "description"))
        {
            target = "description";
            action = PASS_STRING_OR_DROP;
        }
        else if (depth == 2 && matches(key, "parameters"))
        {
            target = "input_schema";
            parametrized = true;
            action = PASS_OBJECT;
        }
        else
        {
            action = unprefix(key);
        }

        return action;
    }

    @Override
    protected int breakpoint(
        CharSequence member)
    {
        return breakpoints != null ? capture(member) : DROP;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        breakpoints.fromCanonical(this, value);

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.ADVANCED;

        if (depth == 2)
        {
            if (!parametrized)
            {
                emit(BIT_SCHEMA_KEY, JsonEvent.KEY_NAME, "input_schema");
                emit(BIT_SCHEMA_START, JsonEvent.START_OBJECT);
                emit(BIT_SCHEMA_TYPE_KEY, JsonEvent.KEY_NAME, "type");
                emit(BIT_SCHEMA_TYPE_VALUE, JsonEvent.VALUE_STRING, "object");
                emit(BIT_SCHEMA_END, JsonEvent.END_OBJECT);
            }
        }
        else if (named)
        {
            pass(BIT_END_OBJECT, JsonEvent.END_OBJECT);
        }
        else
        {
            status = Status.REJECTED;
        }

        return status;
    }
}
