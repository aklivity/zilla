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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.REASONING_EFFORT;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.RESPONSE_FORMAT;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.STOP;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TEMPERATURE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.THINKING_BUDGET_TOKENS;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOOL_CHOICE;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_P;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmOpenaiResponsesControlFromCanonical extends LlmFormatFromCanonical
{
    private static final String DIALECT = "openai";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_INCLUDE_USAGE = "include_usage";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_FORMAT = "format";
    private static final String MEMBER_TYPE = "type";
    private static final String TYPE_JSON_SCHEMA = "json_schema";
    private static final String TYPE_JSON_OBJECT = "json_object";
    private static final String TYPE_TEXT = "text";

    private final boolean members;
    private final LlmToolChoice choice;
    private final LlmModelCapability capability;

    private String member;
    private boolean optionsOpen;
    private int effort;
    private int budget;

    LlmOpenaiResponsesControlFromCanonical(
        boolean sameDialect)
    {
        this(MODE_EXTRACT, sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    private LlmOpenaiResponsesControlFromCanonical(
        int mode,
        boolean sameDialect,
        LlmModelCapability capability)
    {
        super(mode, sameDialect);
        this.members = mode == MODE_OBJECT;
        this.choice = new LlmToolChoice();
        this.capability = capability;
        this.effort = LlmEffort.ABSENT;
        this.budget = LlmEffort.ABSENT;
    }

    static LlmOpenaiResponsesControlFromCanonical members(
        boolean sameDialect)
    {
        return members(sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    static LlmOpenaiResponsesControlFromCanonical members(
        boolean sameDialect,
        LlmModelCapability capability)
    {
        return new LlmOpenaiResponsesControlFromCanonical(MODE_OBJECT, sameDialect, capability);
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
        optionsOpen = false;
        effort = LlmEffort.ABSENT;
        budget = LlmEffort.ABSENT;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action;

        if (formatActive(depth))
        {
            action = formatMember(depth, key);
        }
        else if (depth == 1 && matches(key, MEMBER_STREAM_OPTIONS))
        {
            action = DESCEND;
        }
        else if (depth == 2 && matches(key, MEMBER_INCLUDE_USAGE))
        {
            action = DROP;
        }
        else
        {
            action = unprefix(DIALECT, key);

            if (action != DROP && depth == 1)
            {
                member = target;
            }
            else if (action != DROP && !optionsOpen)
            {
                optionsOpen = true;
                member = MEMBER_STREAM_OPTIONS;

                if (members)
                {
                    emit(JsonEvent.KEY_NAME, MEMBER_STREAM_OPTIONS);
                }

                emit(JsonEvent.START_OBJECT);
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
            else if (matches(key, PARALLEL_TOOL_CALLS))
            {
                target = name(key);
                action = PASS_VALUE;
            }
            else if (matches(key, REASONING_EFFORT) || matches(key, THINKING_BUDGET_TOKENS))
            {
                target = null;
                action = capture(key);
            }
            else if (matches(key, TOOL_CHOICE))
            {
                target = name(key);
                action = capture(key);
            }
            else if (matches(key, STOP))
            {
                action = reject(key);
            }
            else if (matches(key, RESPONSE_FORMAT))
            {
                target = MEMBER_TEXT;
                action = formatShared();
            }
        }

        return action;
    }

    @Override
    protected boolean formatObject()
    {
        return true;
    }

    @Override
    protected boolean formatText()
    {
        return true;
    }

    @Override
    protected void openFormat(
        int kind)
    {
        emit(JsonEvent.KEY_NAME, MEMBER_TEXT);
        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, MEMBER_FORMAT);
        emit(JsonEvent.START_OBJECT);
        emit(JsonEvent.KEY_NAME, MEMBER_TYPE);
        emit(JsonEvent.VALUE_STRING, typeOf(kind));
    }

    @Override
    protected void closeFormat()
    {
        emit(JsonEvent.END_OBJECT);
        emit(JsonEvent.END_OBJECT);
    }

    @Override
    protected int schemaMember(
        CharSequence key)
    {
        target = name(key);

        return PASS_VALUE;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        Status status = Status.ADVANCED;

        if (matches(capturedMember(), MEMBER_TYPE))
        {
            status = formatCaptured(value);
        }
        else if (matches(capturedMember(), REASONING_EFFORT))
        {
            effort = LlmReasoning.effortOf(value);
            status = effort != LlmEffort.INVALID ? Status.ADVANCED : rejected(REASONING_EFFORT);
        }
        else if (matches(capturedMember(), THINKING_BUDGET_TOKENS))
        {
            budget = LlmReasoning.budgetOf(value);
            status = budget != LlmEffort.INVALID ? Status.ADVANCED : rejected(THINKING_BUDGET_TOKENS);
        }
        else if (choice.parseCanonical(value))
        {
            emit(JsonEvent.KEY_NAME, TOOL_CHOICE);
            choice.emitResponses(this);
        }
        else
        {
            status = rejected(TOOL_CHOICE);
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
        Status status = Status.ADVANCED;

        if (formatActive(depth))
        {
            status = formatClosed(depth);
        }
        else if (depth == 2)
        {
            if (optionsOpen)
            {
                pass(JsonEvent.END_OBJECT);
            }
        }
        else if (members)
        {
            pass(JsonEvent.END_OBJECT);
        }
        else if (member == null)
        {
            emit(JsonEvent.VALUE_NULL);
        }

        return status;
    }

    private static String typeOf(
        int kind)
    {
        return switch (kind)
        {
        case FORMAT_SCHEMA -> TYPE_JSON_SCHEMA;
        case FORMAT_OBJECT -> TYPE_JSON_OBJECT;
        default -> TYPE_TEXT;
        };
    }
}
