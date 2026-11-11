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

final class LlmAnthropicToolCallFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final int BIT_TYPE_KEY = 1 << 0;
    private static final int BIT_TYPE_VALUE = 1 << 1;
    private static final int BIT_ID_KEY = 1 << 2;
    private static final int BIT_ID_VALUE = 1 << 3;
    private static final int BIT_NAME_KEY = 1 << 4;
    private static final int BIT_NAME_VALUE = 1 << 5;
    private static final int BIT_INPUT_KEY = 1 << 6;
    private static final int BIT_INPUT_START = 1 << 7;
    private static final int BIT_INPUT_END = 1 << 8;
    private static final int BIT_END_OBJECT = 1 << 9;

    private final String type;
    private final LlmCacheBreakpoint breakpoints;

    private String toolCallId;
    private String toolName;
    private boolean opened;
    private boolean argumented;

    LlmAnthropicToolCallFromCanonical(
        String type,
        boolean sameDialect)
    {
        this(type, sameDialect, null);
    }

    LlmAnthropicToolCallFromCanonical(
        String type,
        boolean sameDialect,
        LlmCacheBreakpoint breakpoints)
    {
        super(MODE_OBJECT, sameDialect ? DIALECT : null);
        this.type = type;
        this.breakpoints = breakpoints;
    }

    @Override
    public void reset()
    {
        super.reset();
        toolCallId = null;
        toolName = null;
        opened = false;
        argumented = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (matches(key, "arguments"))
        {
            target = "input";
            argumented = true;
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
        opened = true;
        toolCallId = attribute("tool-call-id");
        toolName = attribute("tool-name");

        emit(BIT_TYPE_KEY, JsonEvent.KEY_NAME, "type");
        emit(BIT_TYPE_VALUE, JsonEvent.VALUE_STRING, type);

        if (toolCallId != null)
        {
            emit(BIT_ID_KEY, JsonEvent.KEY_NAME, "id");
            emit(BIT_ID_VALUE, JsonEvent.VALUE_STRING, toolCallId);
        }

        if (toolName != null)
        {
            emit(BIT_NAME_KEY, JsonEvent.KEY_NAME, "name");
            emit(BIT_NAME_VALUE, JsonEvent.VALUE_STRING, toolName);
        }
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (!argumented &&
            emit(BIT_INPUT_KEY, JsonEvent.KEY_NAME, "input") &&
            emit(BIT_INPUT_START, JsonEvent.START_OBJECT) &&
            emit(BIT_INPUT_END, JsonEvent.END_OBJECT))
        {
            argumented = true;
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onEndObject()
    {
        Status status = Status.REJECTED;

        if (opened)
        {
            final String lateId = toolCallId == null ? attribute("tool-call-id") : null;
            final String lateName = toolName == null ? attribute("tool-name") : null;
            final boolean identified = (lateId == null ||
                    emit(BIT_ID_KEY, JsonEvent.KEY_NAME, "id") &&
                    emit(BIT_ID_VALUE, JsonEvent.VALUE_STRING, lateId)) &&
                (lateName == null ||
                    emit(BIT_NAME_KEY, JsonEvent.KEY_NAME, "name") &&
                    emit(BIT_NAME_VALUE, JsonEvent.VALUE_STRING, lateName));

            if (identified && (toolCallId != null || lateId != null) && (toolName != null || lateName != null) &&
                emit(BIT_END_OBJECT, JsonEvent.END_OBJECT))
            {
                status = Status.ADVANCED;
            }
        }

        return status;
    }
}
