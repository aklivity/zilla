/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TuningTest
{
    private static final long BINDING_ID = 42L;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldDefaultAffinityToOneBitPerWorkerUpToSixtyFour() throws Exception
    {
        int[] counts = { 1, 2, 31, 32, 33, 63, 64 };

        for (int count : counts)
        {
            try (Tuning tuning = new Tuning(folder.newFolder().toPath(), count))
            {
                tuning.reset();

                long expected = count == Long.SIZE ? -1L : (1L << count) - 1L;
                assertThat(String.format("workers=%d", count), tuning.affinity(BINDING_ID), equalTo(expected));
                assertThat(String.format("workers=%d", count), Long.bitCount(tuning.affinity(BINDING_ID)), equalTo(count));
            }
        }
    }

    @Test
    public void shouldDefaultAffinityToFirstSixtyFourWorkersBeyondSixtyFour() throws Exception
    {
        int[] counts = { 65, 96, 128 };

        for (int count : counts)
        {
            try (Tuning tuning = new Tuning(folder.newFolder().toPath(), count))
            {
                tuning.reset();

                assertThat(String.format("workers=%d", count), tuning.affinity(BINDING_ID), equalTo(-1L));
            }
        }
    }

    @Test
    public void shouldRoundTripExplicitAffinityWithHighBits() throws Exception
    {
        long[] masks = { 1L << 31, 1L << 32, 1L << 63, (1L << 32) | 1L, -1L };

        try (Tuning tuning = new Tuning(folder.newFolder().toPath(), 64))
        {
            tuning.reset();

            for (long mask : masks)
            {
                tuning.affinity(BINDING_ID, mask);

                assertThat(String.format("mask=%x", mask), tuning.affinity(BINDING_ID), equalTo(mask));
            }
        }
    }
}
