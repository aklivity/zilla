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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;

public class LlmOpenaiReasoningTest
{
    @Test
    public void shouldResolveNothingWithoutEffortOrBudget()
    {
        assertThat(LlmOpenaiReasoning.effort(LlmModelCapability.UNSPECIFIED, LlmEffort.ABSENT, LlmEffort.ABSENT), nullValue());
    }

    @Test
    public void shouldKeepEveryEffortTheTargetOffers()
    {
        for (String effort : new String[] {"none", "minimal", "low", "medium", "high", "xhigh"})
        {
            assertThat(LlmOpenaiReasoning.effort(LlmModelCapability.UNSPECIFIED, LlmEffort.of(effort), LlmEffort.ABSENT),
                equalTo(effort));
        }
    }

    @Test
    public void shouldClampMaxToXhigh()
    {
        assertThat(LlmOpenaiReasoning.effort(LlmModelCapability.UNSPECIFIED, LlmEffort.MAX, LlmEffort.ABSENT),
            equalTo("xhigh"));
    }

    @Test
    public void shouldPreferEffortOverBudget()
    {
        assertThat(LlmOpenaiReasoning.effort(LlmModelCapability.UNSPECIFIED, LlmEffort.LOW, 30000), equalTo("low"));
    }

    @Test
    public void shouldDeriveEffortFromBudgetWithDocumentedLadder()
    {
        final LlmModelCapability capability = LlmModelCapability.UNSPECIFIED;

        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 0), equalTo("none"));
        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 512), equalTo("minimal"));
        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 2048), equalTo("low"));
        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 10000), equalTo("medium"));
        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 30000), equalTo("high"));
    }

    @Test
    public void shouldDeriveEffortFromBudgetWithConfiguredLadder()
    {
        final int[] ladder = {UNSET, UNSET, 2000, 3000, 4000, UNSET, UNSET};
        final LlmModelCapability capability = new LlmModelCapability(true, null, UNSET, UNSET, ladder);

        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 3500), equalTo("medium"));
        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 1000), equalTo("minimal"));
    }

    @Test
    public void shouldDropBudgetWithoutUsableLadder()
    {
        final int[] ladder = {UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET};
        final LlmModelCapability capability = new LlmModelCapability(true, null, UNSET, UNSET, ladder);

        assertThat(LlmOpenaiReasoning.effort(capability, LlmEffort.ABSENT, 4096), nullValue());
    }
}
