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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.agrona.DeadlineTimerWheel;
import org.junit.Test;

public class EngineTimerWheelTest
{
    private static final int SESSIONS = 10_000;
    private static final long CONNECT_SPREAD_MILLIS = 1_000L;
    private static final long MAXIMUM_WHEEL_BYTES = 16L * 1024 * 1024;

    @Test
    public void shouldBoundMemoryWhenManyTimersShareTicks() throws Exception
    {
        final long now = 1_000_000L;
        final DeadlineTimerWheel wheel = newWheel(now);

        for (int session = 0; session < SESSIONS; session++)
        {
            final long connectedAt = now + session * CONNECT_SPREAD_MILLIS / SESSIONS;
            wheel.scheduleTimer(connectedAt + 90_000L);
            wheel.scheduleTimer(connectedAt + 60_000L);
            wheel.scheduleTimer(connectedAt + 10_000L);
        }

        assertEquals(3L * SESSIONS, wheel.timerCount());
        assertTrue("timer wheel holds " + wheelBytes(wheel) + " bytes", wheelBytes(wheel) <= MAXIMUM_WHEEL_BYTES);
    }

    @Test
    public void shouldExpireEveryTimerOnceAtOrAfterItsDeadline()
    {
        final long now = 1_000_000L;
        final DeadlineTimerWheel wheel = newWheel(now);
        final long[] deadlines = new long[3 * SESSIONS];

        int timers = 0;
        for (int session = 0; session < SESSIONS; session++)
        {
            final long connectedAt = now + session * CONNECT_SPREAD_MILLIS / SESSIONS;
            for (long delay : new long[] {90_000L, 60_000L, 10_000L})
            {
                deadlines[timers] = connectedAt + delay;
                final long timerId = wheel.scheduleTimer(deadlines[timers]);
                assertTrue(timerId > 0L);
                timers++;
            }
        }

        final int[] expired = new int[1];
        for (long time = now; time <= now + 100_000L; time += 10L)
        {
            wheel.poll(time, (unit, at, timerId) ->
            {
                expired[0]++;
                return true;
            }, Integer.MAX_VALUE);
        }

        assertEquals(deadlines.length, expired[0]);
        assertEquals(0L, wheel.timerCount());
    }

    private static DeadlineTimerWheel newWheel(
        long now)
    {
        return new DeadlineTimerWheel(MILLISECONDS, now, EngineWorker.TIMER_WHEEL_TICK_MILLIS, EngineWorker.TIMER_WHEEL_TICKS);
    }

    private static long wheelBytes(
        DeadlineTimerWheel wheel) throws Exception
    {
        final Field field = DeadlineTimerWheel.class.getDeclaredField("wheel");
        field.setAccessible(true);

        return ((long[]) field.get(wheel)).length * Long.BYTES;
    }
}
