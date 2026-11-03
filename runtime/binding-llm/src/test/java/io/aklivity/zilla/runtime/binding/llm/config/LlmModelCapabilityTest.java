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

import static io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability.UNSET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.junit.Test;

public class LlmModelCapabilityTest
{
    private static final int EFFORT = 1 << LlmMember.REASONING_EFFORT;
    private static final int BUDGET = 1 << LlmMember.THINKING_BUDGET_TOKENS;
    private static final int[] EFFORT_FIRST = {LlmMember.REASONING_EFFORT, LlmMember.THINKING_BUDGET_TOKENS};
    private static final int[] BUDGET_FIRST = {LlmMember.THINKING_BUDGET_TOKENS, LlmMember.REASONING_EFFORT};

    @Test
    public void shouldListNoMemberAsUnsupportedByDefault()
    {
        assertThat(LlmModelCapability.UNSPECIFIED.unsupported, equalTo(0));

        for (int member = 0; member < LlmMember.COUNT; member++)
        {
            assertThat(LlmModelCapability.UNSPECIFIED.unsupported(member), equalTo(false));
        }
    }

    @Test
    public void shouldListMembersAsUnsupported()
    {
        final LlmModelCapability capability = unsupported(LlmMember.maskOf(List.of("seed", "response_format")));

        assertThat(capability.unsupported(LlmMember.SEED), equalTo(true));
        assertThat(capability.unsupported(LlmMember.RESPONSE_FORMAT), equalTo(true));
        assertThat(capability.unsupported(LlmMember.STOP), equalTo(false));
    }

    @Test
    public void shouldExcludeNothingWithoutGroups()
    {
        assertThat(LlmModelCapability.UNSPECIFIED.excluded(EFFORT | BUDGET), equalTo(0));
    }

    @Test
    public void shouldExcludeLaterMemberWhenBothArePresent()
    {
        final LlmModelCapability effortFirst = exclusive(EFFORT_FIRST);
        final LlmModelCapability budgetFirst = exclusive(BUDGET_FIRST);

        assertThat(effortFirst.excluded(EFFORT | BUDGET), equalTo(BUDGET));
        assertThat(budgetFirst.excluded(EFFORT | BUDGET), equalTo(EFFORT));
    }

    @Test
    public void shouldExcludeNothingWhenOnlyOneIsPresent()
    {
        final LlmModelCapability effortFirst = exclusive(EFFORT_FIRST);

        assertThat(effortFirst.excluded(EFFORT), equalTo(0));
        assertThat(effortFirst.excluded(BUDGET), equalTo(0));
        assertThat(effortFirst.excluded(0), equalTo(0));
    }

    @Test
    public void shouldExcludeEachGroupIndependently()
    {
        final LlmModelCapability capability = new LlmModelCapability(true, null, UNSET, UNSET, null, LlmServiceTier.ALL, 0,
            new int[][] {
                {LlmMember.REASONING_EFFORT, LlmMember.THINKING_BUDGET_TOKENS},
                {LlmMember.SEED, LlmMember.TOP_K, LlmMember.USER}});

        final int present = EFFORT | BUDGET | 1 << LlmMember.TOP_K | 1 << LlmMember.USER;

        assertThat(capability.excluded(present), equalTo(BUDGET | 1 << LlmMember.USER));
    }

    private static LlmModelCapability unsupported(
        int mask)
    {
        return new LlmModelCapability(true, null, UNSET, UNSET, null, LlmServiceTier.ALL, mask, new int[0][]);
    }

    private static LlmModelCapability exclusive(
        int[] group)
    {
        return new LlmModelCapability(true, null, UNSET, UNSET, null, LlmServiceTier.ALL, 0, new int[][] {group});
    }
}
