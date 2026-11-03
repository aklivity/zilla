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

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;

final class LlmOpenaiReasoning
{
    static String effort(
        LlmModelCapability capability,
        int requestedEffort,
        int requestedBudget)
    {
        int effort = requestedEffort;

        if (effort == LlmEffort.ABSENT && requestedBudget != LlmEffort.ABSENT)
        {
            effort = LlmReasoning.effortFor(requestedBudget,
                capability.ladder != null ? capability.ladder : LlmReasoning.DOCUMENTED_LADDER);
        }

        return effort != LlmEffort.ABSENT ? LlmEffort.nameOf(LlmEffort.clamp(effort, LlmEffort.NONE, LlmEffort.XHIGH)) : null;
    }

    private LlmOpenaiReasoning()
    {
    }
}
