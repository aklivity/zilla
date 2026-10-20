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

final class LlmGeminiToolCallFromCanonical extends LlmToolTransform
{
    static final String SIGNATURE_PLACEHOLDER = "skip_thought_signature_validator";

    private static final String DIALECT = "gemini";
    private static final String MEMBER_SIGNATURE = "thoughtSignature";
    private static final String MEMBER_WIRE_ID = "gemini:id";

    private boolean placeholder;
    private boolean opened;
    private boolean named;
    private boolean argumented;
    private boolean called;
    private boolean signed;

    LlmGeminiToolCallFromCanonical(
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect);
    }

    void placeholder(
        boolean placeholder)
    {
        this.placeholder = placeholder;
    }

    @Override
    public void reset()
    {
        super.reset();
        opened = false;
        named = false;
        argumented = false;
        called = false;
        signed = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "arguments"))
        {
            target = "args";
            argumented = true;
            action = PASS_OBJECT;
        }
        else if (matches(key, MEMBER_WIRE_ID))
        {
            action = HOLD_STRING;
        }
        else
        {
            endCall();

            action = unprefix(DIALECT, key);

            if (action != DROP && MEMBER_SIGNATURE.equals(target))
            {
                signed = true;
            }
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
        opened = true;

        emit(JsonEvent.KEY_NAME, "functionCall");
        emit(JsonEvent.START_OBJECT);

        final String toolName = attribute("tool-name");

        if (toolName != null)
        {
            named = true;
            emit(JsonEvent.KEY_NAME, "name");
            emit(JsonEvent.VALUE_STRING, toolName);
        }
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

        if (opened && attribute("tool-name") != null)
        {
            endCall();

            if (placeholder && !signed)
            {
                emit(JsonEvent.KEY_NAME, MEMBER_SIGNATURE);
                emit(JsonEvent.VALUE_STRING, SIGNATURE_PLACEHOLDER);
            }

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

            final String toolName = attribute("tool-name");

            if (!named && toolName != null)
            {
                named = true;
                emit(JsonEvent.KEY_NAME, "name");
                emit(JsonEvent.VALUE_STRING, toolName);
            }

            if (!argumented)
            {
                argumented = true;

                emit(JsonEvent.KEY_NAME, "args");
                emit(JsonEvent.START_OBJECT);
                emit(JsonEvent.END_OBJECT);
            }

            final String toolCallId = held() != null ? held() : attribute("tool-call-id");

            if (toolCallId != null)
            {
                emit(JsonEvent.KEY_NAME, "id");
                emit(JsonEvent.VALUE_STRING, toolCallId);
            }

            emit(JsonEvent.END_OBJECT);
        }
    }
}
