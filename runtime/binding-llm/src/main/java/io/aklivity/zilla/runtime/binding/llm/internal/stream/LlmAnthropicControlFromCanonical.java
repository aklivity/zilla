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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.PARALLEL_TOOL_CALLS;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.STOP;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TEMPERATURE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_K;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_P;

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmAnthropicControlFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "anthropic";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_STOP_SEQUENCES = "stop_sequences";
    private static final double TEMPERATURE_MINIMUM = 0.0;
    private static final double TEMPERATURE_MAXIMUM = 1.0;
    private static final String MEMBER_TYPE = "type";
    private static final String MEMBER_NAME = "name";
    private static final String TYPE_AUTO = "auto";
    private static final String TYPE_ANY = "any";
    private static final String TYPE_NONE = "none";
    private static final String TYPE_TOOL = "tool";

    static final int PARALLEL_UNSET = -1;
    static final int PARALLEL_DISABLED = 0;
    static final int PARALLEL_ENABLED = 1;

    private final boolean members;
    private final LlmToolChoice choice;

    private String member;
    private String choiceType;
    private int parallelCalls;

    LlmAnthropicControlFromCanonical(
        boolean sameDialect)
    {
        this(MODE_EXTRACT, sameDialect);
    }

    private LlmAnthropicControlFromCanonical(
        int mode,
        boolean sameDialect)
    {
        super(mode, sameDialect);
        this.members = mode == MODE_OBJECT;
        this.choice = new LlmToolChoice();
        this.parallelCalls = PARALLEL_UNSET;
    }

    static LlmAnthropicControlFromCanonical members(
        boolean sameDialect)
    {
        return new LlmAnthropicControlFromCanonical(MODE_OBJECT, sameDialect);
    }

    String member()
    {
        return member;
    }

    String choiceType()
    {
        return choiceType;
    }

    int parallelCalls()
    {
        return parallelCalls;
    }

    @Override
    public void reset()
    {
        super.reset();
        member = null;
        choiceType = null;
        parallelCalls = PARALLEL_UNSET;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, MEMBER_STREAM_OPTIONS))
        {
            action = DROP;
        }
        else
        {
            action = unprefix(DIALECT, key);

            if (depth == 1 && action != DROP && (!members || member == null))
            {
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

        if (members)
        {
            if (matches(key, TEMPERATURE))
            {
                action = clamp(TEMPERATURE, TEMPERATURE_MINIMUM, TEMPERATURE_MAXIMUM);
            }
            else if (matches(key, TOP_P) || matches(key, TOP_K))
            {
                target = name(key);
                action = PASS_VALUE;
            }
            else if (matches(key, STOP))
            {
                target = MEMBER_STOP_SEQUENCES;
                action = DESCEND_ELEMENTS;
            }
            else if (matches(key, TOOL_CHOICE) || matches(key, PARALLEL_TOOL_CALLS))
            {
                target = TOOL_CHOICE;
                action = capture(key);
            }
        }

        return action;
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

        if (choice.parseCanonical(value) && choice.kind() != LlmToolChoice.ALLOWED)
        {
            choiceType = choice.kind() == LlmToolChoice.FUNCTION ? TYPE_TOOL : typeOf(choice.mode());

            emit(JsonEvent.KEY_NAME, TOOL_CHOICE);
            emit(JsonEvent.START_OBJECT);
            emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
            emit(JsonEvent.VALUE_STRING, choiceType);

            if (choice.kind() == LlmToolChoice.FUNCTION)
            {
                emit(JsonEvent.KEY_NAME, MEMBER_NAME);
                emit(JsonEvent.VALUE_STRING, choice.name());
            }

            emit(JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private Status onParallelCalls(
        LlmCapturedValue value)
    {
        final JsonEvent event = value.event(0);
        Status status = Status.ADVANCED;

        if (event == JsonEvent.VALUE_TRUE)
        {
            parallelCalls = PARALLEL_ENABLED;
        }
        else if (event == JsonEvent.VALUE_FALSE)
        {
            parallelCalls = PARALLEL_DISABLED;
        }
        else if (event != JsonEvent.VALUE_NULL)
        {
            status = rejected(PARALLEL_TOOL_CALLS);
        }

        return status;
    }

    private static String typeOf(
        String mode)
    {
        return switch (mode)
        {
        case LlmToolChoice.MODE_REQUIRED -> TYPE_ANY;
        case LlmToolChoice.MODE_NONE -> TYPE_NONE;
        default -> TYPE_AUTO;
        };
    }
}
