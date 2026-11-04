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

final class LlmGeminiMcpToCanonical extends LlmToolTransform
{
    private static final String DIALECT = LlmGeminiDialectFactorySpi.NAME;
    private static final String TYPE_MCP = "mcp";
    private static final String MEMBER_HEADERS = "headers";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer ";

    private String authorization;
    private boolean transported;

    @Override
    public void reset()
    {
        super.reset();
        authorization = null;
        transported = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, "name"))
        {
            target = "label";
            action = PASS_STRING;
        }
        else if (depth == 1 && matches(key, "streamableHttpTransport"))
        {
            transported = true;
            action = DESCEND;
        }
        else if (depth == 2 && matches(key, "url"))
        {
            target = "url";
            action = PASS_STRING;
        }
        else if (depth == 2 && matches(key, MEMBER_HEADERS))
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
        int index = 1;
        boolean opened = false;

        while (value.isObject(0) && value.event(index) == JsonEvent.KEY_NAME)
        {
            final String name = value.text(index);
            final String bearer = bearerToken(name, value.string(index + 1));

            if (bearer != null)
            {
                authorization = bearer;
            }
            else
            {
                if (!opened)
                {
                    emit(JsonEvent.KEY_NAME, DIALECT + ":" + MEMBER_HEADERS);
                    emit(JsonEvent.START_OBJECT);
                    opened = true;
                }

                emit(JsonEvent.KEY_NAME, name);
                emitCaptured(value, index + 1);
            }

            index = value.end(index + 1);
        }

        if (opened)
        {
            emit(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
        emit(JsonEvent.KEY_NAME, "type");
        emit(JsonEvent.VALUE_STRING, TYPE_MCP);
    }

    @Override
    protected Status onClose(
        int depth)
    {
        Status status = Status.ADVANCED;

        if (depth == 1)
        {
            if (transported)
            {
                if (authorization != null)
                {
                    emit(JsonEvent.KEY_NAME, "authorization");
                    emit(JsonEvent.VALUE_STRING, authorization);
                }

                pass(JsonEvent.END_OBJECT);
            }
            else
            {
                status = Status.REJECTED;
            }
        }

        return status;
    }

    private static String bearerToken(
        String name,
        String value)
    {
        return HEADER_AUTHORIZATION.equalsIgnoreCase(name) && value != null && value.startsWith(BEARER)
            ? value.substring(BEARER.length())
            : null;
    }
}
