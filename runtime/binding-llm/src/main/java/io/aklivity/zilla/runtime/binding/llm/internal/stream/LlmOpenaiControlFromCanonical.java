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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.REASONING_EFFORT;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.RESPONSE_FORMAT;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.SEED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.STOP;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TEMPERATURE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.THINKING_BUDGET_TOKENS;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_P;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmOpenaiControlFromCanonical extends LlmToolTransform
{
    private static final String DIALECT = "openai";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";
    private static final int STOP_ENTRIES_MAX = 4;

    private final boolean members;
    private final boolean foreign;
    private final LlmModelCapability capability;

    private String member;
    private int stopEntries;
    private int effort;
    private int budget;

    LlmOpenaiControlFromCanonical(
        boolean sameDialect)
    {
        this(MODE_EXTRACT, sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    private LlmOpenaiControlFromCanonical(
        int mode,
        boolean sameDialect,
        LlmModelCapability capability)
    {
        super(mode, sameDialect);
        this.members = mode == MODE_OBJECT;
        this.foreign = !sameDialect;
        this.capability = capability;
        this.effort = LlmEffort.ABSENT;
        this.budget = LlmEffort.ABSENT;
    }

    static LlmOpenaiControlFromCanonical members(
        boolean sameDialect)
    {
        return members(sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    static LlmOpenaiControlFromCanonical members(
        boolean sameDialect,
        LlmModelCapability capability)
    {
        return new LlmOpenaiControlFromCanonical(MODE_OBJECT, sameDialect, capability);
    }

    String member()
    {
        return member;
    }

    int effort()
    {
        return effort;
    }

    int budget()
    {
        return budget;
    }

    @Override
    public void reset()
    {
        super.reset();
        member = null;
        stopEntries = 0;
        effort = LlmEffort.ABSENT;
        budget = LlmEffort.ABSENT;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (depth == 1 && matches(key, MEMBER_STREAM_OPTIONS))
        {
            target = MEMBER_STREAM_OPTIONS;
            member = target;
            action = DESCEND_KEPT;
        }
        else if (depth == 2 && matches(key, MEMBER_INCLUDE_USAGE))
        {
            target = MEMBER_INCLUDE_USAGE;
            action = PASS_VALUE;
        }
        else
        {
            action = unprefix(DIALECT, key);

            if (depth == 1 && action != DROP)
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
            if (matches(key, TEMPERATURE) || matches(key, TOP_P))
            {
                if (capability.sampling)
                {
                    target = name(key);
                    action = PASS_VALUE;
                }
            }
            else if (matches(key, REASONING_EFFORT) || matches(key, THINKING_BUDGET_TOKENS))
            {
                target = null;
                action = capture(key);
            }
            else if (matches(key, SEED) ||
                matches(key, FREQUENCY_PENALTY) ||
                matches(key, PRESENCE_PENALTY) ||
                matches(key, TOOL_CHOICE) ||
                matches(key, PARALLEL_TOOL_CALLS) ||
                matches(key, RESPONSE_FORMAT))
            {
                target = name(key);
                action = PASS_VALUE;
            }
            else if (matches(key, STOP))
            {
                target = name(key);
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
        Status status = Status.ADVANCED;

        if (matches(capturedMember(), REASONING_EFFORT))
        {
            effort = LlmReasoning.effortOf(value);
            status = effort != LlmEffort.INVALID ? Status.ADVANCED : rejected(REASONING_EFFORT);
        }
        else
        {
            budget = LlmReasoning.budgetOf(value);
            status = budget != LlmEffort.INVALID ? Status.ADVANCED : rejected(THINKING_BUDGET_TOKENS);
        }

        return status;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        if (depth == 2 || members)
        {
            pass(JsonEvent.END_OBJECT);
        }
        else if (member == null)
        {
            emit(JsonEvent.VALUE_NULL);
        }

        return Status.ADVANCED;
    }
}
