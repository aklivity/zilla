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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class PatternsInputTest
{
    private final PatternsInput input = new PatternsInput();

    @Test
    public void shouldExposeValue()
    {
        input.reset("hello", 10L);

        assertThat(input.length(), equalTo(5));
        assertThat(input.charAt(1), equalTo('e'));
        assertThat(input.subSequence(1, 3).toString(), equalTo("el"));
        assertThat(input.toString(), equalTo("hello"));
    }

    @Test
    public void shouldAllowReadsWithinBudget()
    {
        input.reset("hello", 3L);

        input.charAt(0);
        input.charAt(1);
        input.charAt(2);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldRejectReadsBeyondBudget()
    {
        input.reset("hello", 3L);

        input.charAt(0);
        input.charAt(1);
        input.charAt(2);
        input.charAt(3);
    }

    @Test
    public void shouldRestoreBudgetOnReset()
    {
        input.reset("hello", 1L);
        input.charAt(0);

        input.reset("hello", 1L);

        assertThat(input.charAt(0), equalTo('h'));
    }
}
