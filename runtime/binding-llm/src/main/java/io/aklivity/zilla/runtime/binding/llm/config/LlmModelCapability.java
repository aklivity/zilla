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

public final class LlmModelCapability
{
    public static final int UNSET = -1;

    public static final LlmModelCapability UNSPECIFIED = new LlmModelCapability(true, null, UNSET, UNSET, null);

    public final boolean sampling;
    public final LlmThinking thinking;
    public final int budgetMinimum;
    public final int budgetMaximum;
    public final int[] ladder;

    public LlmModelCapability(
        boolean sampling,
        LlmThinking thinking,
        int budgetMinimum,
        int budgetMaximum,
        int[] ladder)
    {
        this.sampling = sampling;
        this.thinking = thinking;
        this.budgetMinimum = budgetMinimum;
        this.budgetMaximum = budgetMaximum;
        this.ladder = ladder;
    }
}
