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

import static io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability.UNSET;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmReasoning
{
    static final int[] DOCUMENTED_LADDER = {UNSET, UNSET, 1024, 8192, 24576, UNSET, UNSET};

    static int effortOf(
        LlmCapturedValue value)
    {
        final JsonEvent event = value.event(0);
        int effort = LlmEffort.INVALID;

        if (event == JsonEvent.VALUE_NULL)
        {
            effort = LlmEffort.ABSENT;
        }
        else if (event == JsonEvent.VALUE_STRING)
        {
            effort = LlmEffort.of(value.text(0));
        }

        return effort;
    }

    static int budgetOf(
        LlmCapturedValue value)
    {
        final JsonEvent event = value.event(0);
        int budget = LlmEffort.INVALID;

        if (event == JsonEvent.VALUE_NULL)
        {
            budget = LlmEffort.ABSENT;
        }
        else if (event == JsonEvent.VALUE_NUMBER)
        {
            budget = budgetOf(value.text(0));
        }

        return budget;
    }

    static int budgetOf(
        String text)
    {
        long budget = text.isEmpty() ? LlmEffort.INVALID : 0;

        for (int index = 0; budget != LlmEffort.INVALID && index < text.length(); index++)
        {
            final char digit = text.charAt(index);

            budget = digit >= '0' && digit <= '9'
                ? Math.min(budget * 10 + digit - '0', Integer.MAX_VALUE)
                : LlmEffort.INVALID;
        }

        return (int) budget;
    }

    static int budgetFor(
        int effort,
        int[] ladder)
    {
        int budget = UNSET;

        for (int distance = 0; budget == UNSET && distance < LlmEffort.COUNT; distance++)
        {
            final int below = effort - distance;
            final int above = effort + distance;

            if (below > LlmEffort.NONE && ladder[below] != UNSET)
            {
                budget = ladder[below];
            }
            else if (above < LlmEffort.COUNT && ladder[above] != UNSET)
            {
                budget = ladder[above];
            }
        }

        return budget;
    }

    static int effortFor(
        int budget,
        int[] ladder)
    {
        int effort = budget == 0 ? LlmEffort.NONE : LlmEffort.ABSENT;

        for (int candidate = LlmEffort.MINIMAL; budget != 0 && candidate < LlmEffort.COUNT; candidate++)
        {
            if (ladder[candidate] != UNSET)
            {
                if (ladder[candidate] <= budget)
                {
                    effort = candidate;
                }
                else if (effort == LlmEffort.ABSENT)
                {
                    effort = LlmEffort.MINIMAL;
                }
            }
        }

        return effort;
    }

    private LlmReasoning()
    {
    }
}
