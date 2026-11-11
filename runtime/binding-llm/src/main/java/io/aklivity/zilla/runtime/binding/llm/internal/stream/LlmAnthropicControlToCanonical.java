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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.SERVICE_TIER;
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
    private static final String MEMBER_METADATA = "metadata";
    private static final String MEMBER_NATIVE_METADATA = "anthropic:metadata";
    private static final String MEMBER_USER_ID = "user_id";
    private static final String MEMBER_SHARED_USER = "x:user";
    private static final String MEMBER_SHARED_SERVICE_TIER = "x:service_tier";
    private static final String MEMBER_NATIVE_SERVICE_TIER = "anthropic:service_tier";

    private static final int BIT_RESIDUE_KEY = 1 << 0;
    private static final int BIT_RESIDUE_START = 1 << 1;
    private static final int BIT_RESIDUE_END = 1 << 2;
    private static final int BIT_KEY = 1 << 3;
    private static final int BIT_VALUE = 1 << 4;
    private static final int BIT_CHOICE = 1 << 5;
    private static final int BIT_PARALLEL_KEY = 1 << 6;
    private static final int BIT_PARALLEL_VALUE = 1 << 7;
    private static final int BIT_RESIDUE_MEMBERS = 1 << 8;
    private static final int BIT_FORMAT_START = 1 << 9;
    private static final int BIT_FORMAT_TYPE_KEY = 1 << 10;
    private static final int BIT_FORMAT_TYPE_VALUE = 1 << 11;
    private static final int BIT_FORMAT_SCHEMA_KEY = 1 << 12;
    private static final int BIT_FORMAT_SCHEMA_START = 1 << 13;
    private static final int BIT_FORMAT_NAME_KEY = 1 << 14;
    private static final int BIT_FORMAT_NAME_VALUE = 1 << 15;
    private static final int BIT_END_INNER = 1 << 16;
    private static final int BIT_END = 1 << 17;

    private static final int REPLACING_STREAM = 0;
    private static final int REPLACING_FORMAT = 1;

    private static final int OUTPUT_NONE = 0;
    private static final int OUTPUT_RESIDUE = 1;
    private static final int OUTPUT_LIFTED = 2;

    private static final int CONTAINER_OUTPUT_CONFIG = 0;
    private static final int CONTAINER_THINKING = 1;
    private static final int CONTAINER_METADATA = 2;

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

    @Override
    protected boolean resumable()
    {
        return true;
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
        else if (depth == 1 && matches(key, MEMBER_METADATA))
        {
            container = CONTAINER_METADATA;
            target = MEMBER_NATIVE_METADATA;
            action = DESCEND_OR_PASS;
        }
        else if (depth == 1 && matches(key, SERVICE_TIER))
        {
            action = capture(key);
        }
        else if (depth == 2)
        {
            action = switch (container)
            {
            case CONTAINER_THINKING -> thinkingMember(key);
            case CONTAINER_METADATA -> metadataMember(key);
            default -> outputConfigMember(key);
            };
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
            emitFormatWrapper();
        }

        return Status.ADVANCED;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        Status status = Status.ADVANCED;

        if (matches(capturedMember(), SERVICE_TIER))
        {
            onServiceTier(value);
        }
        else if (matches(capturedMember(), MEMBER_TYPE) && container == CONTAINER_THINKING)
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
            if (emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE))
            {
                emitCaptured(BIT_VALUE, value, 0);
            }
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
            if (emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_STREAM) && emit(BIT_VALUE, event))
            {
                streamSeen = true;
                streaming = event == JsonEvent.VALUE_TRUE;
            }

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
            if (emit(BIT_END_INNER, JsonEvent.END_OBJECT))
            {
                emit(BIT_END, JsonEvent.END_OBJECT);
            }
        }
        else if (depth == 2)
        {
            onContainerClose();
        }
        else
        {
            pass(BIT_END, JsonEvent.END_OBJECT);
        }

        return Status.ADVANCED;
    }

    private int outputConfigMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_FORMAT))
        {
            replacing = REPLACING_FORMAT;
            action = DESCEND_OR_REPLACE;

            if (closeResidue() && emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_RESPONSE_FORMAT))
            {
                output = OUTPUT_LIFTED;
            }
        }
        else if (matches(key, MEMBER_EFFORT))
        {
            target = MEMBER_SHARED_REASONING_EFFORT;
            action = PASS_STRING_OR_DROP;

            if (closeResidue())
            {
                output = OUTPUT_LIFTED;
            }
        }
        else
        {
            target = name(key);
            action = PASS_VALUE;

            if (openResidue())
            {
                output = OUTPUT_RESIDUE;
            }
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
            action = rangeInteger(MEMBER_SHARED_THINKING_BUDGET, 0, Integer.MAX_VALUE);

            if (closeResidue())
            {
                output = OUTPUT_LIFTED;
                thinkingType = TYPE_BUDGET;
            }
        }
        else
        {
            target = name(key);
            action = PASS_VALUE;

            if (openResidue())
            {
                output = OUTPUT_RESIDUE;
            }
        }

        return action;
    }

    private int metadataMember(
        CharSequence key)
    {
        int action;

        if (matches(key, MEMBER_USER_ID))
        {
            target = MEMBER_SHARED_USER;
            action = PASS_STRING_OR_DROP;

            if (closeResidue())
            {
                output = OUTPUT_LIFTED;
            }
        }
        else
        {
            target = name(key);
            action = PASS_VALUE;

            if (openResidue())
            {
                output = OUTPUT_RESIDUE;
            }
        }

        return action;
    }

    private void onServiceTier(
        LlmCapturedValue value)
    {
        final String tier = LlmServiceTiers.canonicalAnthropic(value.string(0));

        if (tier != null)
        {
            if (emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_SERVICE_TIER))
            {
                emit(BIT_VALUE, JsonEvent.VALUE_STRING, tier);
            }
        }
        else
        {
            if (emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_NATIVE_SERVICE_TIER))
            {
                emitCaptured(BIT_VALUE, value, 0);
            }
        }
    }

    private void onThinkingType(
        LlmCapturedValue value)
    {
        final String type = value.string(0);

        if (TYPE_DISABLED.equals(type))
        {
            if (closeResidue() &&
                emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_REASONING_EFFORT) &&
                emit(BIT_VALUE, JsonEvent.VALUE_STRING, EFFORT_NONE))
            {
                output = OUTPUT_LIFTED;
            }
        }
        else if (TYPE_ENABLED.equals(type))
        {
            thinkingType = thinkingType == TYPE_BUDGET ? TYPE_BUDGET : TYPE_PENDING;
        }
        else
        {
            if (openResidue() &&
                emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
                emitCaptured(BIT_VALUE, value, 0))
            {
                output = OUTPUT_RESIDUE;
            }
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
        final boolean enabled = thinkingType == TYPE_PENDING;
        final boolean opened = output == OUTPUT_RESIDUE;
        boolean closed = true;

        if (enabled)
        {
            closed = (opened || emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey()) &&
                emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT)) &&
                emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
                emit(BIT_VALUE, JsonEvent.VALUE_STRING, TYPE_ENABLED) &&
                emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
        }
        else if (opened)
        {
            closed = emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
        }
        else if (output == OUTPUT_NONE)
        {
            closed = emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey()) &&
                emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT) &&
                emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
        }

        if (closed)
        {
            output = OUTPUT_NONE;
            thinkingType = TYPE_UNSET;
        }
    }

    private String residueKey()
    {
        return switch (container)
        {
        case CONTAINER_THINKING -> MEMBER_NATIVE_THINKING;
        case CONTAINER_METADATA -> MEMBER_NATIVE_METADATA;
        default -> MEMBER_NATIVE_OUTPUT_CONFIG;
        };
    }

    private boolean openResidue()
    {
        return output == OUTPUT_RESIDUE ||
            emit(BIT_RESIDUE_KEY, JsonEvent.KEY_NAME, residueKey()) &&
            emit(BIT_RESIDUE_START, JsonEvent.START_OBJECT);
    }

    private boolean closeResidue()
    {
        return output != OUTPUT_RESIDUE || emit(BIT_RESIDUE_END, JsonEvent.END_OBJECT);
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

        if (emitLifted(typed, flagged, disabled))
        {
            emitResidue(value, typed, named, flagged);
        }
    }

    private boolean emitLifted(
        boolean typed,
        boolean flagged,
        JsonEvent disabled)
    {
        return (!typed ||
                emit(BIT_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_TOOL_CHOICE) &&
                choice.emitCanonical(this, BIT_CHOICE)) &&
            (!flagged ||
                emit(BIT_PARALLEL_KEY, JsonEvent.KEY_NAME, MEMBER_SHARED_PARALLEL) &&
                emit(BIT_PARALLEL_VALUE, disabled == JsonEvent.VALUE_TRUE ? JsonEvent.VALUE_FALSE : JsonEvent.VALUE_TRUE));
    }

    private boolean emitFormatWrapper()
    {
        return emit(BIT_FORMAT_START, JsonEvent.START_OBJECT) &&
            emit(BIT_FORMAT_TYPE_KEY, JsonEvent.KEY_NAME, MEMBER_TYPE) &&
            emit(BIT_FORMAT_TYPE_VALUE, JsonEvent.VALUE_STRING, TYPE_JSON_SCHEMA) &&
            emit(BIT_FORMAT_SCHEMA_KEY, JsonEvent.KEY_NAME, MEMBER_JSON_SCHEMA) &&
            emit(BIT_FORMAT_SCHEMA_START, JsonEvent.START_OBJECT) &&
            emit(BIT_FORMAT_NAME_KEY, JsonEvent.KEY_NAME, MEMBER_NAME) &&
            emit(BIT_FORMAT_NAME_VALUE, JsonEvent.VALUE_STRING, DEFAULT_NAME);
    }

    private boolean emitResidue(
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
                    emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE);
                    emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.START_OBJECT);
                }

                emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.KEY_NAME, key);
                emitLoopCaptured(BIT_RESIDUE_MEMBERS, value, index + 1);
            }

            index = value.end(index + 1);
        }

        if (residue)
        {
            emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.END_OBJECT);
        }
        else if (!typed && !flagged)
        {
            emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.KEY_NAME, MEMBER_NATIVE_TOOL_CHOICE);
            emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.START_OBJECT);
            emitLoop(BIT_RESIDUE_MEMBERS, JsonEvent.END_OBJECT);
        }

        return endLoop(BIT_RESIDUE_MEMBERS);
    }
}
