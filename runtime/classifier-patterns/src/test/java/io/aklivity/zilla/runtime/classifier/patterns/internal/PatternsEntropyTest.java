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
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class PatternsEntropyTest
{
    private final PatternsEntropy entropy = new PatternsEntropy();

    @Test
    public void shouldMeasureEmptyAsZero()
    {
        assertThat(entropy.measure("", 0, 0), equalTo(0.0));
    }

    @Test
    public void shouldMeasureRepeatedCharacterAsZero()
    {
        assertThat(entropy.measure("aaaaaaaa", 0, 8), equalTo(0.0));
    }

    @Test
    public void shouldMeasureTwoEquallyLikelyCharactersAsOneBit()
    {
        assertThat(entropy.measure("abababab", 0, 8), closeTo(1.0, 0.0001));
    }

    @Test
    public void shouldMeasureFourEquallyLikelyCharactersAsTwoBits()
    {
        assertThat(entropy.measure("abcdabcd", 0, 8), closeTo(2.0, 0.0001));
    }

    @Test
    public void shouldMeasureUnevenDistribution()
    {
        assertThat(entropy.measure("aaab", 0, 4), closeTo(0.8113, 0.0001));
    }

    @Test
    public void shouldMeasureRegionOnly()
    {
        assertThat(entropy.measure("zzzzabcdzzzz", 4, 8), closeTo(2.0, 0.0001));
    }

    @Test
    public void shouldMeasureRegionLongerThanBuffer()
    {
        String text = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(8);

        assertThat(entropy.measure(text, 0, text.length()), closeTo(Math.log(62) / Math.log(2), 0.0001));
    }

    @Test
    public void shouldMeasureAgainAfterGrowing()
    {
        entropy.measure("abcdefghijklmnopqrstuvwxyz".repeat(10), 0, 260);

        assertThat(entropy.measure("abab", 0, 4), closeTo(1.0, 0.0001));
    }
}
