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
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.binding.llm.config.LlmThinking;

final class LlmGeminiThinking
{
    private static final String[] LEVELS = {null, "MINIMAL", "LOW", "MEDIUM", "HIGH"};

    private String level;
    private int budget;

    String level()
    {
        return level;
    }

    int budget()
    {
        return budget;
    }

    void resolve(
        LlmModelCapability capability,
        int requestedEffort,
        int requestedBudget)
    {
        final boolean levelled = capability.thinking == null || capability.thinking == LlmThinking.LEVEL;
        final boolean zero = requestedBudget == 0;
        final int effort = zero && requestedEffort == LlmEffort.ABSENT ? LlmEffort.NONE : requestedEffort;
        final int budget = zero ? LlmEffort.ABSENT : requestedBudget;

        this.level = null;
        this.budget = UNSET;

        if (effort != LlmEffort.ABSENT && (levelled || budget == LlmEffort.ABSENT))
        {
            if (levelled)
            {
                level = LEVELS[LlmEffort.clamp(effort, LlmEffort.MINIMAL, LlmEffort.HIGH)];
            }
            else
            {
                this.budget = effort == LlmEffort.NONE ? off(capability) : ladder(capability, effort);
            }
        }
        else if (budget != LlmEffort.ABSENT)
        {
            this.budget = range(capability, budget);
        }
    }

    private static int off(
        LlmModelCapability capability)
    {
        return Math.max(0, capability.budgetMinimum);
    }

    private static int ladder(
        LlmModelCapability capability,
        int effort)
    {
        final int[] ladder = capability.ladder != null ? capability.ladder : LlmReasoning.DOCUMENTED_LADDER;
        final int budget = LlmReasoning.budgetFor(effort, ladder);

        return budget != UNSET ? range(capability, budget) : UNSET;
    }

    private static int range(
        LlmModelCapability capability,
        int budget)
    {
        final int minimum = Math.max(0, capability.budgetMinimum);
        final int maximum = capability.budgetMaximum != UNSET ? capability.budgetMaximum : Integer.MAX_VALUE;

        return Math.max(minimum, Math.min(maximum, budget));
    }
}
