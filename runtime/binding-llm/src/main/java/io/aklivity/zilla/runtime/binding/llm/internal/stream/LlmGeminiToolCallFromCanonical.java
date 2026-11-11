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
    private static final int BIT_OPEN_KEY = 1 << 0;
    private static final int BIT_OPEN_START = 1 << 1;
    private static final int BIT_OPEN_NAME_KEY = 1 << 2;
    private static final int BIT_OPEN_NAME_VALUE = 1 << 3;
    private static final int BIT_CALL_NAME_KEY = 1 << 4;
    private static final int BIT_CALL_NAME_VALUE = 1 << 5;
    private static final int BIT_CALL_ARGS_KEY = 1 << 6;
    private static final int BIT_CALL_ARGS_START = 1 << 7;
    private static final int BIT_CALL_ARGS_END = 1 << 8;
    private static final int BIT_CALL_ID_KEY = 1 << 9;
    private static final int BIT_CALL_ID_VALUE = 1 << 10;
    private static final int BIT_CALL_END = 1 << 11;
    private static final int BIT_SIGNATURE_KEY = 1 << 12;
    private static final int BIT_SIGNATURE_VALUE = 1 << 13;
    private static final int BIT_END_OBJECT = 1 << 14;

    private boolean placeholder;
    private boolean opened;
    private boolean named;
    private boolean argumented;
    private boolean called;
    private boolean signed;

    LlmGeminiToolCallFromCanonical(
        boolean sameDialect)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
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

            action = unprefix(key);

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
        final String toolName = attribute("tool-name");

        if (emit(BIT_OPEN_KEY, JsonEvent.KEY_NAME, "functionCall") &&
            emit(BIT_OPEN_START, JsonEvent.START_OBJECT) &&
            (toolName == null ||
                emit(BIT_OPEN_NAME_KEY, JsonEvent.KEY_NAME, "name") &&
                emit(BIT_OPEN_NAME_VALUE, JsonEvent.VALUE_STRING, toolName)))
        {
            opened = true;
            named = toolName != null;
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
                emit(BIT_SIGNATURE_KEY, JsonEvent.KEY_NAME, MEMBER_SIGNATURE);
                emit(BIT_SIGNATURE_VALUE, JsonEvent.VALUE_STRING, SIGNATURE_PLACEHOLDER);
            }

            emit(BIT_END_OBJECT, JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private void endCall()
    {
        if (opened && !called && emitName() && emitArguments() && emitId() && emit(BIT_CALL_END, JsonEvent.END_OBJECT))
        {
            called = true;
        }
    }

    private boolean emitName()
    {
        final String toolName = attribute("tool-name");
        boolean written = named || toolName == null;

        if (!written &&
            emit(BIT_CALL_NAME_KEY, JsonEvent.KEY_NAME, "name") &&
            emit(BIT_CALL_NAME_VALUE, JsonEvent.VALUE_STRING, toolName))
        {
            named = true;
            written = true;
        }

        return written;
    }

    private boolean emitArguments()
    {
        boolean written = argumented;

        if (!written &&
            emit(BIT_CALL_ARGS_KEY, JsonEvent.KEY_NAME, "args") &&
            emit(BIT_CALL_ARGS_START, JsonEvent.START_OBJECT) &&
            emit(BIT_CALL_ARGS_END, JsonEvent.END_OBJECT))
        {
            argumented = true;
            written = true;
        }

        return written;
    }

    private boolean emitId()
    {
        final String toolCallId = held() != null ? held() : attribute("tool-call-id");

        return toolCallId == null ||
            emit(BIT_CALL_ID_KEY, JsonEvent.KEY_NAME, "id") &&
            emit(BIT_CALL_ID_VALUE, JsonEvent.VALUE_STRING, toolCallId);
    }
}
