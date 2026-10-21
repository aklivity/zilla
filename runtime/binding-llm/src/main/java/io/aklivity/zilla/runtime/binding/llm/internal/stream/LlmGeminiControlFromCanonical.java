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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.FREQUENCY_PENALTY;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.PARALLEL_TOOL_CALLS;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.PRESENCE_PENALTY;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.SEED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.STOP;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TEMPERATURE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_K;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_P;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmGeminiControlFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "gemini";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_GENERATION_CONFIG = "generationConfig";
    private static final String MEMBER_GENERATION_CONFIG_SNAKE = "generation_config";
    private static final String MEMBER_TOOL_CONFIG = "toolConfig";
    private static final String MEMBER_TOOL_CONFIG_SNAKE = "tool_config";
    private static final String MEMBER_FUNCTION_CALLING_CONFIG = "functionCallingConfig";
    private static final String MEMBER_MODE = "mode";
    private static final String MEMBER_ALLOWED_FUNCTION_NAMES = "allowedFunctionNames";
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_ANY = "ANY";
    private static final String MODE_NONE = "NONE";
    private static final String MEMBER_TOP_P = "topP";
    private static final String MEMBER_TOP_K = "topK";
    private static final String MEMBER_STOP_SEQUENCES = "stopSequences";
    private static final String MEMBER_FREQUENCY_PENALTY = "frequencyPenalty";
    private static final String MEMBER_PRESENCE_PENALTY = "presencePenalty";
    private static final int STOP_ENTRIES_MAX = 5;
    private static final double SEED_MINIMUM = Integer.MIN_VALUE;
    private static final double SEED_MAXIMUM = Integer.MAX_VALUE;

    private final boolean members;
    private final boolean foreign;
    private final LlmToolChoice choice;

    private String member;
    private int stopEntries;

    LlmGeminiControlFromCanonical(
        boolean sameDialect)
    {
        this(MODE_EXTRACT, sameDialect);
    }

    private LlmGeminiControlFromCanonical(
        int mode,
        boolean sameDialect)
    {
        super(mode, sameDialect);
        this.members = mode == MODE_OBJECT;
        this.foreign = !sameDialect;
        this.choice = new LlmToolChoice();
    }

    static LlmGeminiControlFromCanonical members(
        boolean sameDialect)
    {
        return new LlmGeminiControlFromCanonical(MODE_OBJECT, sameDialect);
    }

    String member()
    {
        return member;
    }

    @Override
    public void reset()
    {
        super.reset();
        member = null;
        stopEntries = 0;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && (matches(key, MEMBER_STREAM) || matches(key, MEMBER_STREAM_OPTIONS)))
        {
            action = DROP;
        }
        else
        {
            action = unprefix(DIALECT, key);

            if (depth == 1 && action != DROP)
            {
                if (target != null && matches(target, MEMBER_GENERATION_CONFIG_SNAKE))
                {
                    target = MEMBER_GENERATION_CONFIG;
                }
                else if (target != null && matches(target, MEMBER_TOOL_CONFIG_SNAKE))
                {
                    target = MEMBER_TOOL_CONFIG;
                }

                member = target;
            }
        }

        return action;
    }

    @Override
    protected int shared(
        CharSequence key)
    {
        int action = DROP;

        if (members && (matches(key, TOOL_CHOICE) || matches(key, PARALLEL_TOOL_CALLS)))
        {
            target = MEMBER_TOOL_CONFIG;
            action = capture(key);
        }
        else if (members)
        {
            nest(MEMBER_GENERATION_CONFIG);

            if (matches(key, TEMPERATURE))
            {
                target = TEMPERATURE;
                action = PASS_VALUE;
            }
            else if (matches(key, TOP_P))
            {
                target = MEMBER_TOP_P;
                action = PASS_VALUE;
            }
            else if (matches(key, TOP_K))
            {
                target = MEMBER_TOP_K;
                action = PASS_VALUE;
            }
            else if (matches(key, FREQUENCY_PENALTY))
            {
                target = MEMBER_FREQUENCY_PENALTY;
                action = PASS_VALUE;
            }
            else if (matches(key, PRESENCE_PENALTY))
            {
                target = MEMBER_PRESENCE_PENALTY;
                action = PASS_VALUE;
            }
            else if (matches(key, SEED))
            {
                action = rangeInteger(SEED, SEED_MINIMUM, SEED_MAXIMUM);
            }
            else if (matches(key, STOP))
            {
                target = MEMBER_STOP_SEQUENCES;
                stopEntries = 0;
                action = DESCEND_ELEMENTS;
            }
        }

        return action;
    }

    @Override
    protected int element(
        int depth)
    {
        return foreign && ++stopEntries > STOP_ENTRIES_MAX ? reject(STOP) : PASS_VALUE;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        return matches(capturedMember(), PARALLEL_TOOL_CALLS) ? onParallelCalls(value) : onToolChoice(value);
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (members)
        {
            pass(JsonEvent.END_OBJECT);
        }
        else if (member == null)
        {
            emit(JsonEvent.VALUE_NULL);
        }

        return Status.ADVANCED;
    }

    private Status onToolChoice(
        LlmCapturedValue value)
    {
        Status status = Status.REJECTED;

        if (choice.parseCanonical(value))
        {
            emit(JsonEvent.KEY_NAME, MEMBER_TOOL_CONFIG);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.KEY_NAME, MEMBER_FUNCTION_CALLING_CONFIG);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.KEY_NAME, MEMBER_MODE);
            emit(JsonEvent.VALUE_STRING, modeOf(choice));

            if (choice.kind() == LlmToolChoice.FUNCTION || choice.kind() == LlmToolChoice.ALLOWED && !choice.names().isEmpty())
            {
                emit(JsonEvent.KEY_NAME, MEMBER_ALLOWED_FUNCTION_NAMES);
                emit(JsonEvent.START_ARRAY);

                if (choice.kind() == LlmToolChoice.FUNCTION)
                {
                    emit(JsonEvent.VALUE_STRING, choice.name());
                }
                else
                {
                    for (int index = 0; index < choice.names().size(); index++)
                    {
                        emit(JsonEvent.VALUE_STRING, choice.names().get(index));
                    }
                }

                emit(JsonEvent.END_ARRAY);
            }

            emit(JsonEvent.END_OBJECT);
            emit(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private Status onParallelCalls(
        LlmCapturedValue value)
    {
        final JsonEvent event = value.event(0);

        return event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_NULL ? Status.ADVANCED : rejected(PARALLEL_TOOL_CALLS);
    }

    private static String modeOf(
        LlmToolChoice choice)
    {
        final String mode;

        if (choice.kind() == LlmToolChoice.FUNCTION)
        {
            mode = MODE_ANY;
        }
        else
        {
            mode = switch (choice.mode())
            {
            case LlmToolChoice.MODE_REQUIRED -> MODE_ANY;
            case LlmToolChoice.MODE_NONE -> MODE_NONE;
            default -> MODE_AUTO;
            };
        }

        return mode;
    }
}
