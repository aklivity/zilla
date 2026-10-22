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

final class LlmAnthropicThinking
{
    static final String TYPE_ENABLED = "enabled";
    static final String TYPE_DISABLED = "disabled";

    private static final int BUDGET_MINIMUM = 1024;
    private static final LlmThinking THINKING_DEFAULT = LlmThinking.ADAPTIVE_DISABLED;

    private String effort;
    private String type;
    private int budget;

    String effort()
    {
        return effort;
    }

    String type()
    {
        return type;
    }

    int budget()
    {
        return budget;
    }

    void resolve(
        LlmModelCapability capability,
        int requestedEffort,
        int requestedBudget,
        int maxTokens)
    {
        final LlmThinking thinking = capability.thinking != null ? capability.thinking : THINKING_DEFAULT;
        final boolean zero = requestedBudget == 0;
        final int effort = zero && requestedEffort == LlmEffort.ABSENT ? LlmEffort.NONE : requestedEffort;
        final int budget = zero ? LlmEffort.ABSENT : requestedBudget;

        this.effort = null;
        this.type = null;
        this.budget = UNSET;

        if (effort == LlmEffort.NONE)
        {
            switchOff(thinking);
        }
        else if (effort != LlmEffort.ABSENT || budget != LlmEffort.ABSENT)
        {
            switchOn(capability, thinking, effort, budget, maxTokens);
        }
    }

    private void switchOff(
        LlmThinking thinking)
    {
        switch (thinking)
        {
        case ADAPTIVE:
            effort = LlmEffort.nameOf(LlmEffort.LOW);
            break;
        case ADAPTIVE_ENABLED:
        case ADAPTIVE_DISABLED:
            type = TYPE_DISABLED;
            break;
        default:
            break;
        }
    }

    private void switchOn(
        LlmModelCapability capability,
        LlmThinking thinking,
        int requestedEffort,
        int requestedBudget,
        int maxTokens)
    {
        final boolean effortSupported = thinking != LlmThinking.ENABLED;
        final boolean enabled = thinking == LlmThinking.ENABLED || thinking == LlmThinking.ADAPTIVE_ENABLED;

        if (requestedEffort != LlmEffort.ABSENT && effortSupported)
        {
            effort = LlmEffort.nameOf(LlmEffort.clamp(requestedEffort, LlmEffort.LOW, LlmEffort.MAX));
        }

        if (enabled)
        {
            final int minimum = capability.budgetMinimum != UNSET ? capability.budgetMinimum : BUDGET_MINIMUM;
            final int maximum = capability.budgetMaximum != UNSET
                ? Math.min(capability.budgetMaximum, maxTokens - 1)
                : maxTokens - 1;

            final int wanted = requestedBudget != LlmEffort.ABSENT
                ? requestedBudget
                : requestedEffort != LlmEffort.ABSENT && !effortSupported
                    ? budgetFor(capability, requestedEffort, minimum, maximum)
                    : UNSET;

            if (wanted != UNSET && minimum <= maximum)
            {
                type = TYPE_ENABLED;
                budget = Math.max(minimum, Math.min(maximum, wanted));
            }
        }
    }

    private static int budgetFor(
        LlmModelCapability capability,
        int effort,
        int minimum,
        int maximum)
    {
        final int budget;

        if (capability.ladder != null)
        {
            budget = LlmReasoning.budgetFor(effort, capability.ladder);
        }
        else if (effort <= LlmEffort.LOW)
        {
            budget = minimum;
        }
        else if (effort == LlmEffort.MEDIUM)
        {
            budget = (minimum + maximum) / 2;
        }
        else
        {
            budget = maximum;
        }

        return budget;
    }
}
