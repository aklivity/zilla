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
    private static final String MEMBER_OUTPUT_CONFIG = "output_config";
    private static final String MEMBER_NATIVE_OUTPUT_CONFIG = "anthropic:output_config";
    private static final String MEMBER_SHARED_RESPONSE_FORMAT = "x:response_format";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_SCHEMA = "schema";
    private static final String MEMBER_JSON_SCHEMA = "json_schema";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String DEFAULT_NAME = "response";
    private static final String MEMBER_THINKING = "thinking";
    private static final String MEMBER_NATIVE_THINKING = "anthropic:thinking";
    private static final String MEMBER_EFFORT = "effort";
    private static final String MEMBER_BUDGET_TOKENS = "budget_tokens";
    private static final String MEMBER_SHARED_REASONING_EFFORT = "x:reasoning_effort";
    private static final String MEMBER_SHARED_THINKING_BUDGET = "x:thinking_budget_tokens";
    private static final String TYPE_DISABLED = "disabled";
    private static final String TYPE_ENABLED = "enabled";
    private static final String EFFORT_NONE = "none";

    private static final int REPLACING_STREAM = 0;
    private static final int REPLACING_FORMAT = 1;

    private static final int OUTPUT_NONE = 0;
    private static final int OUTPUT_RESIDUE = 1;
    private static final int OUTPUT_LIFTED = 2;

    private static final int CONTAINER_OUTPUT_CONFIG = 0;
    private static final int CONTAINER_THINKING = 1;

    private static final int TYPE_UNSET = 0;
    private static final int TYPE_PENDING = 1;
    private static final int TYPE_BUDGET = 2;

    private final LlmToolChoice choice;

    private boolean streamSeen;
    private boolean streaming;
    private int replacing;
    private int output;
    private int container;
    private int thinkingType;

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
        output = OUTPUT_NONE;
        container = CONTAINER_OUTPUT_CONFIG;
        thinkingType = TYPE_UNSET;
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
            replacing = REPLACING_STREAM;
            action = REPLACE_VALUE;
        }
        else if (depth == 1 && matches(key, TOOL_CHOICE))
        {
            action = capture(key);
        }
        else if (depth == 1 && matches(key, MEMBER_OUTPUT_CONFIG))
        {
            container = CONTAINER_OUTPUT_CONFIG;
            target = MEMBER_NATIVE_OUTPUT_CONFIG;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 1 && matches(key, MEMBER_THINKING))
        {
            container = CONTAINER_THINKING;
            target = MEMBER_NATIVE_THINKING;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 2)
        {
            action = container == CONTAINER_THINKING ? thinkingMember(key) : outputConfigMember(key);
        }
        else if (depth == 3)
        {
            action = formatMember(key);
        }
        else
        {
            action = prefix(DIALECT, key);
        }

        return action;
    }

    @Override
    protected Status onDescend(
        int depth)
    {
        if (depth == 2)
        {
            output = OUTPUT_NONE;
            thinkingType = TYPE_UNSET;
        }
        else
        {
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
            emit(JsonEvent.VALUE_STRING, TYPE_JSON_SCHEMA);
            emit(JsonEvent.KEY_NAME, MEMBER_JSON_SCHEMA);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.KEY_NAME, MEMBER_NAME);
            emit(JsonEvent.VALUE_STRING, DEFAULT_NAME);
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        Status status = Status.ADVANCED;

        if (matches(capturedMember(), MEMBER_TYPE) && container == CONTAINER_THINKING)
        {
            onThinkingType(value);
        }
        else if (matches(capturedMember(), MEMBER_TYPE))
        {
            status = TYPE_JSON_SCHEMA.equals(value.string(0)) ? Status.ADVANCED : Status.REJECTED;
        }
        else
        {
            onToolChoiceCaptured(value);
        }

        return status;
    }

    private void onToolChoiceCaptured(
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

        if (replacing == REPLACING_STREAM && flag)
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
        if (depth == 3)
        {
            emit(JsonEvent.END_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }
        else if (depth == 2)
        {
            onContainerClose();
        }
        else
        {
            pass(JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    private int outputConfigMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_FORMAT))
        {
            closeResidue();
            output = OUTPUT_LIFTED;
            replacing = REPLACING_FORMAT;
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_RESPONSE_FORMAT);
            action = DESCEND_OR_REPLACE;
        }
        else if (matches(key, MEMBER_EFFORT))
        {
            closeResidue();
            output = OUTPUT_LIFTED;
            target = MEMBER_SHARED_REASONING_EFFORT;
            action = PASS_STRING_OR_DROP;
        }
        else
        {
            openResidue();
            target = name(key);
            action = PASS_VALUE;
        }

        return action;
    }

    private int thinkingMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_TYPE))
        {
            action = capture(key);
        }
        else if (matches(key, MEMBER_BUDGET_TOKENS))
        {
            closeResidue();
            output = OUTPUT_LIFTED;
            thinkingType = TYPE_BUDGET;
            action = rangeInteger(MEMBER_SHARED_THINKING_BUDGET, 0, Integer.MAX_VALUE);
        }
        else
        {
            openResidue();
            target = name(key);
            action = PASS_VALUE;
        }

        return action;
    }

    private void onThinkingType(
        LlmCapturedValue value)
    {
        final String type = value.string(0);

        if (TYPE_DISABLED.equals(type))
        {
            closeResidue();
            output = OUTPUT_LIFTED;
            emit(JsonEvent.KEY_NAME, MEMBER_SHARED_REASONING_EFFORT);
            emit(JsonEvent.VALUE_STRING, EFFORT_NONE);
        }
        else if (TYPE_ENABLED.equals(type))
        {
            thinkingType = thinkingType == TYPE_BUDGET ? TYPE_BUDGET : TYPE_PENDING;
        }
        else
        {
            openResidue();
            emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
            emitCaptured(value, 0);
        }
    }

    private int formatMember(
        CharSequence key)
    {
        int action = DROP;

        if (matches(key, MEMBER_SCHEMA))
        {
            target = MEMBER_SCHEMA;
            action = PASS_VALUE;
        }
        else if (matches(key, MEMBER_TYPE))
        {
            action = capture(key);
        }

        return action;
    }

    private void onContainerClose()
    {
        if (thinkingType == TYPE_PENDING)
        {
            openResidue();
            emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
            emit(JsonEvent.VALUE_STRING, TYPE_ENABLED);
        }

        if (output == OUTPUT_RESIDUE)
        {
            emit(JsonEvent.END_OBJECT);
        }
        else if (output == OUTPUT_NONE)
        {
            emit(JsonEvent.KEY_NAME, residueKey());
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.END_OBJECT);
        }

        output = OUTPUT_NONE;
        thinkingType = TYPE_UNSET;
    }

    private String residueKey()
    {
        return container == CONTAINER_THINKING ? MEMBER_NATIVE_THINKING : MEMBER_NATIVE_OUTPUT_CONFIG;
    }

    private void openResidue()
    {
        if (output != OUTPUT_RESIDUE)
        {
            output = OUTPUT_RESIDUE;
            emit(JsonEvent.KEY_NAME, residueKey());
            emit(JsonEvent.START_OBJECT);
        }
    }

    private void closeResidue()
    {
        if (output == OUTPUT_RESIDUE)
        {
            emit(JsonEvent.END_OBJECT);
        }
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
