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

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;

public class LlmReasoningTest
{
    private static final int[] LADDER = {UNSET, UNSET, 1024, 8192, 24576, UNSET, UNSET};

    @Test
    public void shouldParseBudget()
    {
        assertThat(LlmReasoning.budgetOf("0"), equalTo(0));
        assertThat(LlmReasoning.budgetOf("4096"), equalTo(4096));
        assertThat(LlmReasoning.budgetOf("2147483647"), equalTo(Integer.MAX_VALUE));
        assertThat(LlmReasoning.budgetOf("99999999999999"), equalTo(Integer.MAX_VALUE));
    }

    @Test
    public void shouldNotParseBudgetThatIsNotANonNegativeInteger()
    {
        assertThat(LlmReasoning.budgetOf(""), equalTo(LlmEffort.INVALID));
        assertThat(LlmReasoning.budgetOf("-1"), equalTo(LlmEffort.INVALID));
        assertThat(LlmReasoning.budgetOf("1.5"), equalTo(LlmEffort.INVALID));
        assertThat(LlmReasoning.budgetOf("1e3"), equalTo(LlmEffort.INVALID));
    }

    @Test
    public void shouldFindBudgetOfEffortPresentInLadder()
    {
        assertThat(LlmReasoning.budgetFor(LlmEffort.LOW, LADDER), equalTo(1024));
        assertThat(LlmReasoning.budgetFor(LlmEffort.MEDIUM, LADDER), equalTo(8192));
        assertThat(LlmReasoning.budgetFor(LlmEffort.HIGH, LADDER), equalTo(24576));
    }

    @Test
    public void shouldFindBudgetOfNearestEffortInLadder()
    {
        assertThat(LlmReasoning.budgetFor(LlmEffort.MINIMAL, LADDER), equalTo(1024));
        assertThat(LlmReasoning.budgetFor(LlmEffort.XHIGH, LADDER), equalTo(24576));
        assertThat(LlmReasoning.budgetFor(LlmEffort.MAX, LADDER), equalTo(24576));
    }

    @Test
    public void shouldPreferLowerEffortOnTie()
    {
        final int[] sparse = {UNSET, UNSET, 1024, UNSET, UNSET, 4096, UNSET};

        assertThat(LlmReasoning.budgetFor(LlmEffort.MEDIUM, sparse), equalTo(1024));
        assertThat(LlmReasoning.budgetFor(LlmEffort.HIGH, sparse), equalTo(4096));
    }

    @Test
    public void shouldFindNoBudgetInEmptyLadder()
    {
        final int[] empty = {UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET};

        assertThat(LlmReasoning.budgetFor(LlmEffort.HIGH, empty), equalTo(UNSET));
    }

    @Test
    public void shouldFindEffortOfBudget()
    {
        assertThat(LlmReasoning.effortFor(0, LADDER), equalTo(LlmEffort.NONE));
        assertThat(LlmReasoning.effortFor(512, LADDER), equalTo(LlmEffort.MINIMAL));
        assertThat(LlmReasoning.effortFor(1024, LADDER), equalTo(LlmEffort.LOW));
        assertThat(LlmReasoning.effortFor(8191, LADDER), equalTo(LlmEffort.LOW));
        assertThat(LlmReasoning.effortFor(8192, LADDER), equalTo(LlmEffort.MEDIUM));
        assertThat(LlmReasoning.effortFor(24576, LADDER), equalTo(LlmEffort.HIGH));
        assertThat(LlmReasoning.effortFor(100000, LADDER), equalTo(LlmEffort.HIGH));
    }

    @Test
    public void shouldFindNoEffortInEmptyLadder()
    {
        final int[] empty = {UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET};

        assertThat(LlmReasoning.effortFor(4096, empty), equalTo(LlmEffort.ABSENT));
        assertThat(LlmReasoning.effortFor(0, empty), equalTo(LlmEffort.NONE));
    }
}
