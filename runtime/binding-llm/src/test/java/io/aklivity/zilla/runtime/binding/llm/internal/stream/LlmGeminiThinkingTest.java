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
import io.aklivity.zilla.runtime.binding.llm.config.LlmThinking;

public class LlmGeminiThinkingTest
{
    private final LlmGeminiThinking thinking = new LlmGeminiThinking();

    @Test
    public void shouldResolveNothingWithoutEffortOrBudget()
    {
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.ABSENT, LlmEffort.ABSENT);

        assertThat(thinking.level(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldDefaultToLevel()
    {
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.HIGH, LlmEffort.ABSENT);

        assertThat(thinking.level(), equalTo("HIGH"));
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldResolveEveryLevel()
    {
        final String[] levels = {"MINIMAL", "MINIMAL", "LOW", "MEDIUM", "HIGH", "HIGH", "HIGH"};

        for (int effort = LlmEffort.NONE; effort <= LlmEffort.MAX; effort++)
        {
            thinking.resolve(capability(LlmThinking.LEVEL, UNSET, UNSET), effort, LlmEffort.ABSENT);

            assertThat(LlmEffort.nameOf(effort), thinking.level(), equalTo(levels[effort]));
        }
    }

    @Test
    public void shouldResolveBudgetFromDocumentedLadder()
    {
        final LlmModelCapability budgeted = capability(LlmThinking.BUDGET, UNSET, UNSET);
        final int[] budgets = {-1, 1024, 1024, 8192, 24576, 24576, 24576};

        for (int effort = LlmEffort.MINIMAL; effort <= LlmEffort.MAX; effort++)
        {
            thinking.resolve(budgeted, effort, LlmEffort.ABSENT);

            assertThat(LlmEffort.nameOf(effort), thinking.budget(), equalTo(budgets[effort]));
            assertThat(thinking.level(), nullValue());
        }
    }

    @Test
    public void shouldResolveBudgetFromConfiguredLadder()
    {
        final int[] ladder = {UNSET, UNSET, 500, 600, 700, UNSET, UNSET};
        final LlmModelCapability budgeted = new LlmModelCapability(true, LlmThinking.BUDGET, UNSET, UNSET, ladder);

        thinking.resolve(budgeted, LlmEffort.HIGH, LlmEffort.ABSENT);

        assertThat(thinking.budget(), equalTo(700));
    }

    @Test
    public void shouldClampLadderBudgetIntoRange()
    {
        thinking.resolve(capability(LlmThinking.BUDGET, 128, 16000), LlmEffort.HIGH, LlmEffort.ABSENT);

        assertThat(thinking.budget(), equalTo(16000));
    }

    @Test
    public void shouldDropEffortWithoutUsableLadder()
    {
        final int[] ladder = {UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET};
        final LlmModelCapability budgeted = new LlmModelCapability(true, LlmThinking.BUDGET, UNSET, UNSET, ladder);

        thinking.resolve(budgeted, LlmEffort.HIGH, LlmEffort.ABSENT);

        assertThat(thinking.level(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldTurnThinkingOffWhereModelAllowsZero()
    {
        thinking.resolve(capability(LlmThinking.BUDGET, 0, 24576), LlmEffort.NONE, LlmEffort.ABSENT);

        assertThat(thinking.budget(), equalTo(0));
        assertThat(thinking.level(), nullValue());
    }

    @Test
    public void shouldTurnThinkingOffAtMinimumWhereModelDoesNotAllowZero()
    {
        thinking.resolve(capability(LlmThinking.BUDGET, 128, 32768), LlmEffort.NONE, LlmEffort.ABSENT);

        assertThat(thinking.budget(), equalTo(128));
    }

    @Test
    public void shouldTurnThinkingOffForZeroBudget()
    {
        thinking.resolve(capability(LlmThinking.BUDGET, 0, 24576), LlmEffort.ABSENT, 0);

        assertThat(thinking.budget(), equalTo(0));
    }

    @Test
    public void shouldTurnThinkingOffToMinimalLevelForZeroBudget()
    {
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.ABSENT, 0);

        assertThat(thinking.level(), equalTo("MINIMAL"));
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldResolveBudgetOnLevelModelWithoutEffort()
    {
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.ABSENT, 4096);

        assertThat(thinking.level(), nullValue());
        assertThat(thinking.budget(), equalTo(4096));
    }

    @Test
    public void shouldClampBudgetIntoRange()
    {
        final LlmModelCapability ranged = capability(LlmThinking.BUDGET, 128, 24576);

        thinking.resolve(ranged, LlmEffort.ABSENT, 64);
        assertThat(thinking.budget(), equalTo(128));

        thinking.resolve(ranged, LlmEffort.ABSENT, 100000);
        assertThat(thinking.budget(), equalTo(24576));

        thinking.resolve(ranged, LlmEffort.ABSENT, 4096);
        assertThat(thinking.budget(), equalTo(4096));
    }

    @Test
    public void shouldPreferLevelOverBudgetOnLevelModel()
    {
        thinking.resolve(capability(LlmThinking.LEVEL, UNSET, UNSET), LlmEffort.LOW, 4096);

        assertThat(thinking.level(), equalTo("LOW"));
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldPreferBudgetOverEffortOnBudgetModel()
    {
        thinking.resolve(capability(LlmThinking.BUDGET, UNSET, UNSET), LlmEffort.LOW, 4096);

        assertThat(thinking.level(), nullValue());
        assertThat(thinking.budget(), equalTo(4096));
    }

    @Test
    public void shouldNotCarryStateBetweenResolutions()
    {
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.HIGH, LlmEffort.ABSENT);
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.ABSENT, LlmEffort.ABSENT);

        assertThat(thinking.level(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    private static LlmModelCapability capability(
        LlmThinking form,
        int minimum,
        int maximum)
    {
        return new LlmModelCapability(true, form, minimum, maximum, null);
    }
}
