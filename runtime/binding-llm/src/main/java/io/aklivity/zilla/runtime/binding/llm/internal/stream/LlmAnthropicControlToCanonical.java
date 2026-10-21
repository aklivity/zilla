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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmAnthropicControlToCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_NAME = "name";
    private static final String MEMBER_DISABLE_PARALLEL = "disable_parallel_tool_use";
    private static final String MEMBER_NATIVE_TOOL_CHOICE = "anthropic:tool_choice";
    private static final String MEMBER_SHARED_TOOL_CHOICE = "x:tool_choice";
    private static final String MEMBER_SHARED_PARALLEL = "x:parallel_tool_calls";
    private static final String TYPE_AUTO = "auto";
    private static final String TYPE_ANY = "any";
    private static final String TYPE_NONE = "none";
    private static final String TYPE_TOOL = "tool";

    private final LlmToolChoice choice;

    private boolean streamSeen;
    private boolean streaming;

    LlmAnthropicControlToCanonical()
    {
        this.choice = new LlmToolChoice();
    }

    boolean streamSeen()
    {
        return streamSeen;
    }

    boolean streaming()
    {
        return streaming;
    }

    @Override
    public void reset()
    {
        super.reset();
        streamSeen = false;
        streaming = false;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, MEMBER_STREAM))
        {
            target = MEMBER_STREAM;
            action = REPLACE_VALUE;
        }
        else if (depth == 1 && matches(key, TOOL_CHOICE))
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
        if (value.isObject(0))
        {
            splitToolChoice(value);
        }
        else
        {
            emit(JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE);
            emitCaptured(value, 0);
        }

        return Status.ADVANCED;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onReplace(
        JsonEvent event)
    {
        final boolean flag = event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE || event == JsonEvent.VALUE_NULL;
        Status status = Status.REJECTED;

        if (flag)
        {
            streamSeen = true;
            streaming = event == JsonEvent.VALUE_TRUE;
            emit(JsonEvent.KEY_NAME, MEMBER_STREAM);
            emit(event);
            status = Status.ADVANCED;
        }

        return status;
    }

    @Override
    protected Status onClose(
        int depth)
    {
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }

    private void splitToolChoice(
        LlmCapturedValue value)
    {
        final String type = value.string(value.member(0, MEMBER_TYPE));
        final String function = value.string(value.member(0, MEMBER_NAME));
        final int disable = value.member(0, MEMBER_DISABLE_PARALLEL);
        final JsonEvent disabled = value.event(disable);

        choice.clear();

        if (TYPE_AUTO.equals(type))
        {
            choice.mode(LlmToolChoice.MODE_AUTO);
        }
        else if (TYPE_ANY.equals(type))
        {
            choice.mode(LlmToolChoice.MODE_REQUIRED);
        }
        else if (TYPE_NONE.equals(type))
        {
            choice.mode(LlmToolChoice.MODE_NONE);
        }
        else if (TYPE_TOOL.equals(type) && function != null)
        {
            choice.function(function);
        }

        final boolean typed = choice.kind() != LlmToolChoice.UNRECOGNIZED;
        final boolean named = choice.kind() == LlmToolChoice.FUNCTION;
        final boolean flagged = disabled == JsonEvent.VALUE_TRUE || disabled == JsonEvent.VALUE_FALSE;

        if (typed)
        {
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_TOOL_CHOICE);
            choice.emitCanonical(this);
        }

        if (flagged)
        {
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_PARALLEL);
            emit(disabled == JsonEvent.VALUE_TRUE ? JsonEvent.VALUE_FALSE : JsonEvent.VALUE_TRUE);
        }

        emitResidue(value, typed, named, flagged);
    }

    private void emitResidue(
        LlmCapturedValue value,
        boolean typed,
        boolean named,
        boolean flagged)
    {
        boolean residue = false;
        int index = 1;

        while (value.event(index) == JsonEvent.KEY_NAME)
        {
            final String key = value.text(index);
            final boolean consumed = typed && MEMBER_TYPE.equals(key) ||
                named && MEMBER_NAME.equals(key) ||
                flagged && MEMBER_DISABLE_PARALLEL.equals(key);

            if (!consumed)
            {
                if (!residue)
                {
                    residue = true;
                    emit(JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE);
                    emit(JsonEvent.START_OBJECT);
                }

                emit(JsonEvent.KEY_NAME, key);
                emitCaptured(value, index + 1);
            }

            index = value.end(index + 1);
        }

        if (residue)
        {
            emit(JsonEvent.END_OBJECT);
        }
        else if (!typed && !flagged)
        {
            emit(JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }
    }
}
