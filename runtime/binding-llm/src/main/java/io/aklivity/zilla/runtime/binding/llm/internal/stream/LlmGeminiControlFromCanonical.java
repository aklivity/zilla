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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.PRESENCE_PENALTY;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.SEED;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.STOP;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmSharedMembers.TEMPERATURE;
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
}
