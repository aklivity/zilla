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

public class LlmAnthropicThinkingTest
{
    private static final int MAX_TOKENS = 2048;

    private final LlmAnthropicThinking thinking = new LlmAnthropicThinking();

    @Test
    public void shouldResolveNothingWithoutEffortOrBudget()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_ENABLED), LlmEffort.ABSENT, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.effort(), nullValue());
        assertThat(thinking.type(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldResolveEffortNatively()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_DISABLED), LlmEffort.HIGH, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.effort(), equalTo("high"));
        assertThat(thinking.type(), nullValue());
    }

    @Test
    public void shouldClampMinimalEffortToLow()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_DISABLED), LlmEffort.MINIMAL, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.effort(), equalTo("low"));
    }

    @Test
    public void shouldKeepEveryEffortTheModelOffers()
    {
        for (String effort : new String[] {"low", "medium", "high", "xhigh", "max"})
        {
            thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.of(effort), LlmEffort.ABSENT, MAX_TOKENS);

            assertThat(thinking.effort(), equalTo(effort));
        }
    }

    @Test
    public void shouldDefaultToAdaptiveWithDisabled()
    {
        thinking.resolve(LlmModelCapability.UNSPECIFIED, LlmEffort.NONE, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), equalTo("disabled"));
        assertThat(thinking.effort(), nullValue());
    }

    @Test
    public void shouldDisableThinkingForNoneOnAdaptiveWithDisabled()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_DISABLED), LlmEffort.NONE, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), equalTo("disabled"));
        assertThat(thinking.effort(), nullValue());
    }

    @Test
    public void shouldDisableThinkingForNoneOnAdaptiveWithEnabled()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_ENABLED), LlmEffort.NONE, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), equalTo("disabled"));
    }

    @Test
    public void shouldUseLowestEffortForNoneOnAdaptiveOnly()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE), LlmEffort.NONE, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), nullValue());
        assertThat(thinking.effort(), equalTo("low"));
    }

    @Test
    public void shouldOmitThinkingForNoneOnEnabledOnly()
    {
        thinking.resolve(capability(LlmThinking.ENABLED), LlmEffort.NONE, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), nullValue());
        assertThat(thinking.effort(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldTreatZeroBudgetAsNone()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_DISABLED), LlmEffort.ABSENT, 0, MAX_TOKENS);

        assertThat(thinking.type(), equalTo("disabled"));
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldNotEnableThinkingForNoneWithBudget()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_ENABLED), LlmEffort.NONE, 4096, MAX_TOKENS);

        assertThat(thinking.type(), equalTo("disabled"));
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldHonourBudgetWhereEnabledIsAccepted()
    {
        for (LlmThinking form : new LlmThinking[] {LlmThinking.ENABLED, LlmThinking.ADAPTIVE_ENABLED})
        {
            thinking.resolve(capability(form), LlmEffort.ABSENT, 1536, MAX_TOKENS);

            assertThat(thinking.type(), equalTo("enabled"));
            assertThat(thinking.budget(), equalTo(1536));
        }
    }

    @Test
    public void shouldDropBudgetWhereEnabledIsNotAccepted()
    {
        for (LlmThinking form : new LlmThinking[] {LlmThinking.ADAPTIVE, LlmThinking.ADAPTIVE_DISABLED})
        {
            thinking.resolve(capability(form), LlmEffort.ABSENT, 1536, MAX_TOKENS);

            assertThat(thinking.type(), nullValue());
            assertThat(thinking.budget(), equalTo(UNSET));
        }
    }

    @Test
    public void shouldClampBudgetBelowMinimum()
    {
        thinking.resolve(capability(LlmThinking.ENABLED), LlmEffort.ABSENT, 512, MAX_TOKENS);

        assertThat(thinking.budget(), equalTo(1024));
    }

    @Test
    public void shouldClampBudgetAtMaxTokens()
    {
        thinking.resolve(capability(LlmThinking.ENABLED), LlmEffort.ABSENT, MAX_TOKENS, MAX_TOKENS);

        assertThat(thinking.budget(), equalTo(MAX_TOKENS - 1));
    }

    @Test
    public void shouldClampBudgetAboveMaxTokens()
    {
        thinking.resolve(capability(LlmThinking.ENABLED), LlmEffort.ABSENT, 100000, MAX_TOKENS);

        assertThat(thinking.budget(), equalTo(MAX_TOKENS - 1));
    }

    @Test
    public void shouldClampBudgetIntoConfiguredRange()
    {
        final LlmModelCapability ranged = new LlmModelCapability(true, LlmThinking.ENABLED, 1500, 1800, null);

        thinking.resolve(ranged, LlmEffort.ABSENT, 1024, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1500));

        thinking.resolve(ranged, LlmEffort.ABSENT, 2000, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1800));
    }

    @Test
    public void shouldDropBudgetWhenMaxTokensLeavesNoRoom()
    {
        thinking.resolve(capability(LlmThinking.ENABLED), LlmEffort.ABSENT, 2048, 1024);

        assertThat(thinking.type(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldDeriveBudgetFromEffortOnEnabledOnly()
    {
        final LlmModelCapability enabled = capability(LlmThinking.ENABLED);

        thinking.resolve(enabled, LlmEffort.LOW, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.type(), equalTo("enabled"));
        assertThat(thinking.budget(), equalTo(1024));
        assertThat(thinking.effort(), nullValue());

        thinking.resolve(enabled, LlmEffort.MEDIUM, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1535));

        thinking.resolve(enabled, LlmEffort.HIGH, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(2047));

        thinking.resolve(enabled, LlmEffort.MINIMAL, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1024));

        thinking.resolve(enabled, LlmEffort.MAX, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(2047));
    }

    @Test
    public void shouldDeriveBudgetFromConfiguredLadder()
    {
        final int[] ladder = {UNSET, UNSET, 1100, 1300, 1500, UNSET, UNSET};
        final LlmModelCapability enabled = new LlmModelCapability(true, LlmThinking.ENABLED, UNSET, UNSET, ladder);

        thinking.resolve(enabled, LlmEffort.MINIMAL, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1100));

        thinking.resolve(enabled, LlmEffort.HIGH, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1500));

        thinking.resolve(enabled, LlmEffort.MAX, LlmEffort.ABSENT, MAX_TOKENS);
        assertThat(thinking.budget(), equalTo(1500));
    }

    @Test
    public void shouldCapConfiguredLadderAtMaxTokens()
    {
        final int[] ladder = {UNSET, UNSET, 1100, UNSET, 9000, UNSET, UNSET};
        final LlmModelCapability enabled = new LlmModelCapability(true, LlmThinking.ENABLED, UNSET, UNSET, ladder);

        thinking.resolve(enabled, LlmEffort.HIGH, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.budget(), equalTo(MAX_TOKENS - 1));
    }

    @Test
    public void shouldDropEffortWithoutUsableLadder()
    {
        final int[] ladder = {UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET};
        final LlmModelCapability enabled = new LlmModelCapability(true, LlmThinking.ENABLED, UNSET, UNSET, ladder);

        thinking.resolve(enabled, LlmEffort.HIGH, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    @Test
    public void shouldDropEffortWhenMinimumLeavesNoRoom()
    {
        final LlmModelCapability floor = new LlmModelCapability(true, LlmThinking.ENABLED, 4096, UNSET, null);

        thinking.resolve(floor, LlmEffort.HIGH, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.type(), nullValue());
    }

    @Test
    public void shouldPreferBudgetOverEffortOnEnabledOnly()
    {
        thinking.resolve(capability(LlmThinking.ENABLED), LlmEffort.HIGH, 1536, MAX_TOKENS);

        assertThat(thinking.budget(), equalTo(1536));
    }

    @Test
    public void shouldResolveEffortAndBudgetTogetherOnAdaptiveWithEnabled()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_ENABLED), LlmEffort.MEDIUM, 1536, MAX_TOKENS);

        assertThat(thinking.effort(), equalTo("medium"));
        assertThat(thinking.type(), equalTo("enabled"));
        assertThat(thinking.budget(), equalTo(1536));
    }

    @Test
    public void shouldNotCarryStateBetweenResolutions()
    {
        thinking.resolve(capability(LlmThinking.ADAPTIVE_ENABLED), LlmEffort.MEDIUM, 1536, MAX_TOKENS);
        thinking.resolve(capability(LlmThinking.ADAPTIVE_ENABLED), LlmEffort.ABSENT, LlmEffort.ABSENT, MAX_TOKENS);

        assertThat(thinking.effort(), nullValue());
        assertThat(thinking.type(), nullValue());
        assertThat(thinking.budget(), equalTo(UNSET));
    }

    private static LlmModelCapability capability(
        LlmThinking form)
    {
        return new LlmModelCapability(true, form, UNSET, UNSET, null);
    }
}
