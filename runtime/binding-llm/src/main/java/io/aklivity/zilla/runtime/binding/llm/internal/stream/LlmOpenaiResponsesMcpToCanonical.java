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

final class LlmOpenaiResponsesMcpToCanonical extends LlmToolTransform
{
    private static final String DIALECT = LlmOpenaiResponsesDialectFactorySpi.NAME;
    private static final String TYPE_MCP = "mcp";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_ALLOWED_TOOLS = "allowed_tools";
    private static final String MEMBER_TOOLS = "tools";

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_TYPE))
        {
            action = HOLD_STRING;
        }
        else if (matches(key, "server_label"))
        {
            target = "label";
            action = PASS_STRING;
        }
        else if (matches(key, "server_url"))
        {
            target = "url";
            action = PASS_STRING;
        }
        else if (matches(key, "authorization"))
        {
            target = "authorization";
            action = PASS_STRING_OR_DROP;
        }
        else if (matches(key, MEMBER_ALLOWED_TOOLS))
        {
            action = capture(key);
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        if (value.isArray(0))
        {
            emit(JsonEvent.KEY_NAME, MEMBER_TOOLS);
            emitCaptured(value, 0);
        }
        else if (value.event(0) != JsonEvent.VALUE_NULL)
        {
            emit(JsonEvent.KEY_NAME, DIALECT + ":" + MEMBER_ALLOWED_TOOLS);
            emitCaptured(value, 0);
        }

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
        emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
        emit(JsonEvent.VALUE_STRING, TYPE_MCP);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.REJECTED;

        if (TYPE_MCP.equals(held()))
        {
            pass(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }
}
