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

    private static final int[][] NO_GROUPS = new int[0][];

    public static final LlmModelCapability UNSPECIFIED = new LlmModelCapability(true, null, UNSET, UNSET, null);

    public final boolean sampling;
    public final LlmThinking thinking;
    public final int budgetMinimum;
    public final int budgetMaximum;
    public final int[] ladder;
    public final int tiers;
    public final int unsupported;
    private final int[][] exclusive;

    public LlmModelCapability(
        boolean sampling,
        LlmThinking thinking,
        int budgetMinimum,
        int budgetMaximum,
        int[] ladder)
    {
        this(sampling, thinking, budgetMinimum, budgetMaximum, ladder, LlmServiceTier.ALL);
    }

    public LlmModelCapability(
        boolean sampling,
        LlmThinking thinking,
        int budgetMinimum,
        int budgetMaximum,
        int[] ladder,
        int tiers)
    {
        this(sampling, thinking, budgetMinimum, budgetMaximum, ladder, tiers, 0, NO_GROUPS);
    }

    public LlmModelCapability(
        boolean sampling,
        LlmThinking thinking,
        int budgetMinimum,
        int budgetMaximum,
        int[] ladder,
        int tiers,
        int unsupported,
        int[][] exclusive)
    {
        this.sampling = sampling;
        this.thinking = thinking;
        this.budgetMinimum = budgetMinimum;
        this.budgetMaximum = budgetMaximum;
        this.ladder = ladder;
        this.tiers = tiers;
        this.unsupported = unsupported;
        this.exclusive = exclusive;
    }

    public boolean supports(
        int tier)
    {
        return (tiers & 1 << tier) != 0;
    }

    public boolean unsupported(
        int member)
    {
        return (unsupported & 1 << member) != 0;
    }

    public int excluded(
        int present)
    {
        int excluded = 0;

        for (int[] group : exclusive)
        {
            boolean winner = false;

            for (int member : group)
            {
                final int bit = 1 << member;

                if ((present & bit) != 0)
                {
                    excluded |= winner ? bit : 0;
                    winner = true;
                }
            }
        }

        return excluded;
    }
}
