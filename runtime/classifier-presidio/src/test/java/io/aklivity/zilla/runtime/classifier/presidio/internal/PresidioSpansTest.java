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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

import java.util.List;

import org.junit.Test;

public class PresidioSpansTest
{
    @Test
    public void shouldResolveNoFindings()
    {
        assertThat(PresidioSpans.resolve(List.of(), 10), empty());
    }

    @Test
    public void shouldOrderFindingsByStart()
    {
        PresidioFinding later = new PresidioFinding("EMAIL_ADDRESS", 20, 30, 0.9);
        PresidioFinding earlier = new PresidioFinding("PERSON", 0, 4, 0.9);

        assertThat(PresidioSpans.resolve(List.of(later, earlier), 40), contains(earlier, later));
    }

    @Test
    public void shouldKeepAdjacentFindings()
    {
        PresidioFinding first = new PresidioFinding("PERSON", 0, 4, 0.9);
        PresidioFinding second = new PresidioFinding("PERSON", 4, 8, 0.9);

        assertThat(PresidioSpans.resolve(List.of(first, second), 8), contains(first, second));
    }

    @Test
    public void shouldPreferLongerOverlappingFinding()
    {
        PresidioFinding shorter = new PresidioFinding("PERSON", 0, 4, 0.99);
        PresidioFinding longer = new PresidioFinding("PERSON", 0, 9, 0.50);

        assertThat(PresidioSpans.resolve(List.of(shorter, longer), 10), contains(longer));
    }

    @Test
    public void shouldPreferHigherScoreWhenOverlappingFindingsAreSameLength()
    {
        PresidioFinding weaker = new PresidioFinding("PERSON", 0, 4, 0.50);
        PresidioFinding stronger = new PresidioFinding("LOCATION", 0, 4, 0.90);

        assertThat(PresidioSpans.resolve(List.of(weaker, stronger), 10), contains(stronger));
    }

    @Test
    public void shouldDropFindingContainedByAnother()
    {
        PresidioFinding outer = new PresidioFinding("PERSON", 0, 10, 0.80);
        PresidioFinding inner = new PresidioFinding("LOCATION", 2, 5, 0.99);

        assertThat(PresidioSpans.resolve(List.of(inner, outer), 10), contains(outer));
    }

    @Test
    public void shouldDropFindingStartingInsideAnotherThatEndsLater()
    {
        PresidioFinding first = new PresidioFinding("PERSON", 0, 6, 0.80);
        PresidioFinding second = new PresidioFinding("LOCATION", 4, 9, 0.80);

        assertThat(PresidioSpans.resolve(List.of(first, second), 10), contains(first));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectFindingBeyondText()
    {
        PresidioSpans.resolve(List.of(new PresidioFinding("PERSON", 0, 11, 0.9)), 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeStart()
    {
        PresidioSpans.resolve(List.of(new PresidioFinding("PERSON", -1, 4, 0.9)), 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectEmptyFinding()
    {
        PresidioSpans.resolve(List.of(new PresidioFinding("PERSON", 4, 4, 0.9)), 10);
    }
}
