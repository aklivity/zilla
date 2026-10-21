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
package io.aklivity.zilla.runtime.binding.llm.config;

import java.util.List;

public final class LlmMember
{
    public static final int INVALID = -1;
    public static final int TEMPERATURE = 0;
    public static final int TOP_P = 1;
    public static final int TOP_K = 2;
    public static final int STOP = 3;
    public static final int SEED = 4;
    public static final int FREQUENCY_PENALTY = 5;
    public static final int PRESENCE_PENALTY = 6;
    public static final int TOOL_CHOICE = 7;
    public static final int PARALLEL_TOOL_CALLS = 8;
    public static final int RESPONSE_FORMAT = 9;
    public static final int REASONING_EFFORT = 10;
    public static final int THINKING_BUDGET_TOKENS = 11;
    public static final int USER = 12;
    public static final int SERVICE_TIER = 13;
    public static final int COUNT = 14;

    private static final String[] NAMES = {
        "temperature",
        "top_p",
        "top_k",
        "stop",
        "seed",
        "frequency_penalty",
        "presence_penalty",
        "tool_choice",
        "parallel_tool_calls",
        "response_format",
        "reasoning_effort",
        "thinking_budget_tokens",
        "user",
        "service_tier"
    };

    private static final int BEHAVIOURAL = 1 << STOP | 1 << TOOL_CHOICE | 1 << PARALLEL_TOOL_CALLS | 1 << RESPONSE_FORMAT;

    public static int of(
        CharSequence name)
    {
        int found = INVALID;

        for (int member = 0; found == INVALID && member < COUNT; member++)
        {
            if (NAMES[member].contentEquals(name))
            {
                found = member;
            }
        }

        return found;
    }

    public static String nameOf(
        int member)
    {
        return NAMES[member];
    }

    public static boolean behavioural(
        int member)
    {
        return (BEHAVIOURAL & 1 << member) != 0;
    }

    public static int maskOf(
        List<String> names)
    {
        int mask = 0;

        for (String name : names)
        {
            final int member = of(name);

            if (member != INVALID)
            {
                mask |= 1 << member;
            }
        }

        return mask;
    }

    public static int[] groupOf(
        List<String> names)
    {
        return names.stream()
            .mapToInt(LlmMember::of)
            .filter(member -> member != INVALID)
            .toArray();
    }

    private LlmMember()
    {
    }
}
