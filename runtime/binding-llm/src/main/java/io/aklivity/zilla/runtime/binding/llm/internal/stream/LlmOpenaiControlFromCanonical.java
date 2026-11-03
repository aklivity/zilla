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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.SERVICE_TIER;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.STOP;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TEMPERATURE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.THINKING_BUDGET_TOKENS;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_P;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.USER;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmOpenaiControlFromCanonical extends LlmToolTransform
{
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";
    private static final String MEMBER_SAFETY_IDENTIFIER = "safety_identifier";
    private static final String MEMBER_SERVICE_TIER = "service_tier";
    private static final int SAFETY_IDENTIFIER_MAX = 128;
    private static final int STOP_ENTRIES_MAX = 4;

    private final boolean members;
    private final boolean foreign;
    private final LlmModelCapability capability;

    private String member;
    private int stopEntries;
    private int effort;
    private int budget;

    LlmOpenaiControlFromCanonical(
        String dialect,
        boolean sameDialect)
    {
        this(MODE_EXTRACT, dialect, sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    private LlmOpenaiControlFromCanonical(
        int mode,
        String dialect,
        boolean sameDialect,
        LlmModelCapability capability)
    {
        super(mode, dialect);
        this.members = mode == MODE_OBJECT;
        this.foreign = !sameDialect;
        this.capability = capability;
        legality(capability);
        this.effort = LlmEffort.ABSENT;
        this.budget = LlmEffort.ABSENT;
    }

    static LlmOpenaiControlFromCanonical members(
        String dialect,
        boolean sameDialect)
    {
        return members(dialect, sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    static LlmOpenaiControlFromCanonical members(
        String dialect,
        boolean sameDialect,
        LlmModelCapability capability)
    {
        return new LlmOpenaiControlFromCanonical(MODE_OBJECT, dialect, sameDialect, capability);
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
            action = unprefix(key);

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
            else if (matches(key, USER))
            {
                target = MEMBER_SAFETY_IDENTIFIER;
                action = limitString(SAFETY_IDENTIFIER_MAX);
            }
            else if (matches(key, SERVICE_TIER))
            {
                target = MEMBER_SERVICE_TIER;
                action = limitString(LlmServiceTiers.NAME_MAX);
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
    protected String adapted(
        String value)
    {
        return matches(target, MEMBER_SERVICE_TIER) ? LlmServiceTiers.openai(capability, value) : value;
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
