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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_K;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TOP_P;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmGeminiControlFromCanonical extends LlmFormatFromCanonical
{
    private static final String DIALECT = "gemini";
    private static final String MEMBER_STREAM = "stream";
    private static final String MEMBER_STREAM_OPTIONS = "stream_options";
    private static final String MEMBER_GENERATION_CONFIG = "generationConfig";
    private static final String MEMBER_GENERATION_CONFIG_SNAKE = "generation_config";
    private static final String MEMBER_TOOL_CONFIG = "toolConfig";
    private static final String MEMBER_TOOL_CONFIG_SNAKE = "tool_config";
    private static final String MEMBER_THINKING_CONFIG = "thinkingConfig";
    private static final String MEMBER_THINKING_CONFIG_SNAKE = "thinking_config";
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
    private static final String MEMBER_RESPONSE_FORMAT = "responseFormat";
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_MIME_TYPE = "mimeType";
    private static final String MEMBER_TYPE = "type";
    private static final String MIME_JSON = "APPLICATION_JSON";
    private static final String MEMBER_SERVICE_TIER = "serviceTier";
    private static final String MEMBER_SERVICE_TIER_SNAKE = "service_tier";
    private static final int STOP_ENTRIES_MAX = 5;
    private static final double SEED_MINIMUM = Integer.MIN_VALUE;
    private static final double SEED_MAXIMUM = Integer.MAX_VALUE;
    // tool choice, format open and format close run in separate dispatches, so they reuse the same bit values
    private static final int BIT_TOOL_CONFIG_KEY = 1 << 0;
    private static final int BIT_TOOL_CONFIG_START = 1 << 1;
    private static final int BIT_FUNCTION_CALLING_KEY = 1 << 2;
    private static final int BIT_FUNCTION_CALLING_START = 1 << 3;
    private static final int BIT_MODE_KEY = 1 << 4;
    private static final int BIT_MODE_VALUE = 1 << 5;
    private static final int BIT_ALLOWED_NAMES_KEY = 1 << 6;
    private static final int BIT_ALLOWED_NAMES_START = 1 << 7;
    private static final int BIT_ALLOWED_FUNCTION_NAME = 1 << 8;
    private static final int BIT_ALLOWED_NAMES_END = 1 << 9;
    private static final int BIT_FUNCTION_CALLING_END = 1 << 10;
    private static final int BIT_TOOL_CONFIG_END = 1 << 11;
    private static final int BIT_ALLOWED_FUNCTION_NAMES = 1 << 12;
    private static final int BIT_FORMAT_CONFIG_KEY = 1 << 0;
    private static final int BIT_FORMAT_CONFIG_START = 1 << 1;
    private static final int BIT_FORMAT_KEY = 1 << 2;
    private static final int BIT_FORMAT_START = 1 << 3;
    private static final int BIT_FORMAT_TEXT_KEY = 1 << 4;
    private static final int BIT_FORMAT_TEXT_START = 1 << 5;
    private static final int BIT_FORMAT_MIME_KEY = 1 << 6;
    private static final int BIT_FORMAT_MIME_VALUE = 1 << 7;
    private static final int BIT_FORMAT_TEXT_END = 1 << 0;
    private static final int BIT_FORMAT_END = 1 << 1;
    private static final int BIT_FORMAT_CONFIG_END = 1 << 2;
    private static final int BIT_PASS_END_OBJECT = 1 << 3;
    private static final int BIT_EMIT_VALUE_NULL = 1 << 4;

    private final boolean members;
    private final boolean foreign;
    private final LlmToolChoice choice;
    private final LlmSchemaScanner scanner;
    private final LlmModelCapability capability;

    private String member;
    private int stopEntries;
    private int effort;
    private int budget;

    LlmGeminiControlFromCanonical(
        boolean sameDialect)
    {
        this(MODE_EXTRACT, sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    private LlmGeminiControlFromCanonical(
        int mode,
        boolean sameDialect,
        LlmModelCapability capability)
    {
        super(mode, sameDialect ? DIALECT : null, mode == MODE_OBJECT ? new LlmCapturedValue() : null,
            mode == MODE_OBJECT ? new LlmCapturedScalar() : null);
        this.members = mode == MODE_OBJECT;
        this.foreign = !sameDialect;
        this.choice = new LlmToolChoice();
        this.scanner = new LlmSchemaScanner(LlmSchemaScanner.TARGET_GEMINI);
        this.capability = capability;
        legality(capability);
        this.effort = LlmEffort.ABSENT;
        this.budget = LlmEffort.ABSENT;
    }

    static LlmGeminiControlFromCanonical members(
        boolean sameDialect)
    {
        return members(sameDialect, LlmModelCapability.UNSPECIFIED);
    }

    static LlmGeminiControlFromCanonical members(
        boolean sameDialect,
        LlmModelCapability capability)
    {
        return new LlmGeminiControlFromCanonical(MODE_OBJECT, sameDialect, capability);
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

        if (formatActive(depth))
        {
            action = formatMember(depth, key);
        }
        else if (depth == 1 && (matches(key, MEMBER_STREAM) || matches(key, MEMBER_STREAM_OPTIONS)))
        {
            action = DROP;
        }
        else
        {
            action = unprefix(key);

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
                else if (target != null && matches(target, MEMBER_THINKING_CONFIG_SNAKE))
                {
                    target = MEMBER_THINKING_CONFIG;
                }
                else if (target != null && matches(target, MEMBER_SERVICE_TIER_SNAKE))
                {
                    target = MEMBER_SERVICE_TIER;
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

        if (members && matches(key, TOOL_CHOICE))
        {
            target = MEMBER_TOOL_CONFIG;
            action = capture(key);
        }
        else if (members && matches(key, PARALLEL_TOOL_CALLS))
        {
            target = MEMBER_TOOL_CONFIG;
            action = captureScalar(key);
        }
        else if (members && matches(key, RESPONSE_FORMAT))
        {
            target = MEMBER_GENERATION_CONFIG;
            action = formatShared();
        }
        else if (members && (matches(key, REASONING_EFFORT) || matches(key, THINKING_BUDGET_TOKENS)))
        {
            target = null;
            action = captureScalar(key);
        }
        else if (members && matches(key, SERVICE_TIER))
        {
            target = MEMBER_SERVICE_TIER;
            action = limitString(LlmServiceTiers.NAME_MAX);
        }
        else if (members)
        {
            nest(MEMBER_GENERATION_CONFIG);

            if (matches(key, TEMPERATURE))
            {
                if (capability.sampling)
                {
                    target = TEMPERATURE;
                    action = PASS_VALUE;
                }
            }
            else if (matches(key, TOP_P))
            {
                if (capability.sampling)
                {
                    target = MEMBER_TOP_P;
                    action = PASS_VALUE;
                }
            }
            else if (matches(key, TOP_K))
            {
                if (capability.sampling)
                {
                    target = MEMBER_TOP_K;
                    action = PASS_VALUE;
                }
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
    protected boolean formatObject()
    {
        return true;
    }

    @Override
    protected boolean formatText()
    {
        return false;
    }

    @Override
    protected boolean openFormat(
        int kind)
    {
        return emit(BIT_FORMAT_CONFIG_KEY, JsonEvent.KEY_NAME, MEMBER_GENERATION_CONFIG) &&
            emit(BIT_FORMAT_CONFIG_START, JsonEvent.START_OBJECT) &&
            emit(BIT_FORMAT_KEY, JsonEvent.KEY_NAME, MEMBER_RESPONSE_FORMAT) &&
            emit(BIT_FORMAT_START, JsonEvent.START_OBJECT) &&
            emit(BIT_FORMAT_TEXT_KEY, JsonEvent.KEY_NAME, MEMBER_TEXT) &&
            emit(BIT_FORMAT_TEXT_START, JsonEvent.START_OBJECT) &&
            emit(BIT_FORMAT_MIME_KEY, JsonEvent.KEY_NAME, MEMBER_MIME_TYPE) &&
            emit(BIT_FORMAT_MIME_VALUE, JsonEvent.VALUE_STRING, MIME_JSON);
    }

    @Override
    protected boolean closeFormat()
    {
        return emit(BIT_FORMAT_TEXT_END, JsonEvent.END_OBJECT) &&
            emit(BIT_FORMAT_END, JsonEvent.END_OBJECT) &&
            emit(BIT_FORMAT_CONFIG_END, JsonEvent.END_OBJECT);
    }

    @Override
    protected int schemaMember(
        CharSequence key)
    {
        int action = DROP;

        if (matches(key, MEMBER_SCHEMA))
        {
            target = MEMBER_SCHEMA;
            scan(scanner, RESPONSE_FORMAT);
            action = PASS_VALUE;
        }

        return action;
    }

    @Override
    protected String adapted(
        String value)
    {
        return LlmServiceTiers.gemini(capability, value);
    }

    @Override
    protected Status onScalar(
        LlmCapturedScalar value)
    {
        final String captured = capturedMember();
        Status status;

        if (matches(captured, REASONING_EFFORT))
        {
            effort = LlmReasoning.effortOf(value);
            status = effort != LlmEffort.INVALID ? Status.ADVANCED : rejected(REASONING_EFFORT);
        }
        else if (matches(captured, THINKING_BUDGET_TOKENS))
        {
            budget = LlmReasoning.budgetOf(value);
            status = budget != LlmEffort.INVALID ? Status.ADVANCED : rejected(THINKING_BUDGET_TOKENS);
        }
        else
        {
            status = matches(captured, MEMBER_TYPE) ? formatCaptured(value) : onParallelCalls(value);
        }

        return status;
    }

    @Override
    protected Status onCaptured(
        LlmCapturedValue value)
    {
        return onToolChoice(value);
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
        else if (members)
        {
            pass(BIT_PASS_END_OBJECT, JsonEvent.END_OBJECT);
        }
        else if (member == null)
        {
            emit(BIT_EMIT_VALUE_NULL, JsonEvent.VALUE_NULL);
        }

        return status;
    }

    private Status onToolChoice(
        LlmCapturedValue value)
    {
        Status status = Status.REJECTED;

        if (choice.parseCanonical(value))
        {
            emit(BIT_TOOL_CONFIG_KEY, JsonEvent.KEY_NAME, MEMBER_TOOL_CONFIG);
            emit(BIT_TOOL_CONFIG_START, JsonEvent.START_OBJECT);
            emit(BIT_FUNCTION_CALLING_KEY, JsonEvent.KEY_NAME, MEMBER_FUNCTION_CALLING_CONFIG);
            emit(BIT_FUNCTION_CALLING_START, JsonEvent.START_OBJECT);
            emit(BIT_MODE_KEY, JsonEvent.KEY_NAME, MEMBER_MODE);
            emit(BIT_MODE_VALUE, JsonEvent.VALUE_STRING, modeOf(choice));

            if (choice.kind() == LlmToolChoice.FUNCTION || choice.kind() == LlmToolChoice.ALLOWED && !choice.names().isEmpty())
            {
                emit(BIT_ALLOWED_NAMES_KEY, JsonEvent.KEY_NAME, MEMBER_ALLOWED_FUNCTION_NAMES);
                emit(BIT_ALLOWED_NAMES_START, JsonEvent.START_ARRAY);

                if (choice.kind() == LlmToolChoice.FUNCTION)
                {
                    emit(BIT_ALLOWED_FUNCTION_NAME, JsonEvent.VALUE_STRING, choice.name());
                }
                else
                {
                    for (int index = 0; index < choice.names().size(); index++)
                    {
                        emitLoop(BIT_ALLOWED_FUNCTION_NAMES, JsonEvent.VALUE_STRING, choice.names().get(index));
                    }

                    endLoop(BIT_ALLOWED_FUNCTION_NAMES);
                }

                emit(BIT_ALLOWED_NAMES_END, JsonEvent.END_ARRAY);
            }

            emit(BIT_FUNCTION_CALLING_END, JsonEvent.END_OBJECT);
            emit(BIT_TOOL_CONFIG_END, JsonEvent.END_OBJECT);
            status = Status.ADVANCED;
        }

        return status;
    }

    private Status onParallelCalls(
        LlmCapturedScalar value)
    {
        final JsonEvent event = value.event();

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
