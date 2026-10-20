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

final class LlmGeminiToolDefinitionToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";
    private static final int NAMES_DEPTH_MAX = 62;

    private boolean named;
    private long names;

    @Override
    public void reset()
    {
        super.reset();
        named = false;
        names = 0L;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        return depth == 1 ? declarationMember(key) : schemaMember(depth, key);
    }

    private int declarationMember(
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
        else if (matches(key, "parametersJsonSchema") || matches(key, "parameters_json_schema"))
        {
            target = "parameters";
            action = PASS_OBJECT;
        }
        else if (matches(key, "parameters"))
        {
            target = "parameters";
            action = DESCEND_KEPT;
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    private int schemaMember(
        int depth,
        CharSequence key)
    {
        final boolean propertyName = depth <= NAMES_DEPTH_MAX && (names & (1L << depth)) != 0L;
        int action;

        if (propertyName)
        {
            target = key.toString();
            action = DESCEND_KEPT;
        }
        else if (matches(key, "type"))
        {
            target = "type";
            action = CONVERT_STRING;
        }
        else if (matches(key, "properties") && depth < NAMES_DEPTH_MAX)
        {
            names |= 1L << (depth + 1);
            target = "properties";
            action = DESCEND_KEPT;
        }
        else if (matches(key, "items"))
        {
            target = "items";
            action = DESCEND_KEPT;
        }
        else
        {
            target = key.toString();
            action = PASS_VALUE;
        }

        return action;
    }

    @Override
    protected String converted(
        String value)
    {
        return switch (value)
        {
        case "STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT", "NULL" -> value.toLowerCase();
        default -> value;
        };
    }

    @Override
    protected void onOpen()
    {
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, "function");
        emit(JsonEvent.KEY_NAME, "function");
        emit(JsonEvent.START_OBJECT);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.REJECTED;

        if (depth > 1)
        {
            if (depth <= NAMES_DEPTH_MAX)
            {
                names &= ~(1L << depth);
            }

            pass(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }
        else if (named)
        {
            emit(JsonEvent.END_OBJECT);
            pass(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }
}
