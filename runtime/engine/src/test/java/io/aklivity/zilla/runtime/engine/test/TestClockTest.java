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
package io.aklivity.zilla.runtime.engine.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.time.Clock;
import java.time.Instant;

import org.junit.Test;

public class TestClockTest
{
    @Test
    public void shouldHoldFixedTime()
    {
        final TestClock clock = TestClock.of(1000L);

        assertEquals(1000L, clock.millis());
        assertEquals(1000L, clock.millis());
        assertEquals(Instant.ofEpochMilli(1000L), clock.instant());
    }

    @Test
    public void shouldAdvanceExplicitly()
    {
        final TestClock clock = TestClock.of(1000L);

        clock.advanceBy(500L);
        assertEquals(1500L, clock.millis());

        clock.advanceTo(9000L);
        assertEquals(9000L, clock.millis());
    }

    @Test
    public void shouldStepPerRead()
    {
        final TestClock clock = TestClock.stepping(0L, 1000L);

        assertEquals(0L, clock.millis());
        assertEquals(1000L, clock.millis());
        assertEquals(2000L, clock.instant().toEpochMilli());
    }

    @Test
    public void shouldSupplyInstalledClockToInheritingThreads() throws Exception
    {
        final TestClock clock = TestClock.of(42L);
        final Clock[] supplied = new Clock[1];

        TestClock.install(clock);
        try
        {
            final Thread thread = new Thread(() -> supplied[0] = TestClock.supply());
            thread.start();
            thread.join();

            assertSame(clock, TestClock.supply());
            assertSame(clock, supplied[0]);
        }
        finally
        {
            TestClock.uninstall();
        }
    }

    @Test
    public void shouldSupplySystemClockWhenNotInstalled()
    {
        assertSame(Clock.systemUTC().getClass(), TestClock.supply().getClass());
    }
}
