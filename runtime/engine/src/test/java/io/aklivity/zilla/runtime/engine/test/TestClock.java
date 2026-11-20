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

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

public final class TestClock extends Clock
{
    private static final InheritableThreadLocal<Clock> CURRENT = new InheritableThreadLocal<>()
    {
        @Override
        protected Clock initialValue()
        {
            return Clock.systemUTC();
        }
    };

    private final AtomicLong millis;
    private final AtomicLong stepMillis;

    public static TestClock of(
        long startMillis)
    {
        return new TestClock(startMillis, 0L);
    }

    public static TestClock stepping(
        long startMillis,
        long stepMillis)
    {
        return new TestClock(startMillis, stepMillis);
    }

    public static Clock supply()
    {
        return CURRENT.get();
    }

    static void install(
        Clock clock)
    {
        CURRENT.set(requireNonNull(clock));
    }

    static void uninstall()
    {
        CURRENT.remove();
    }

    public void advanceBy(
        long deltaMillis)
    {
        millis.addAndGet(deltaMillis);
    }

    public void advanceTo(
        long timeMillis)
    {
        millis.set(timeMillis);
    }

    public void step(
        long stepMillis)
    {
        this.stepMillis.set(stepMillis);
    }

    public Ticker ticker(
        long periodMillis,
        long stepMillis)
    {
        return new Ticker(periodMillis, stepMillis);
    }

    @Override
    public ZoneId getZone()
    {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(
        ZoneId zone)
    {
        return this;
    }

    @Override
    public long millis()
    {
        return millis.getAndAdd(stepMillis.get());
    }

    @Override
    public Instant instant()
    {
        return Instant.ofEpochMilli(millis());
    }

    private TestClock(
        long startMillis,
        long stepMillis)
    {
        this.millis = new AtomicLong(startMillis);
        this.stepMillis = new AtomicLong(stepMillis);
    }

    public final class Ticker implements AutoCloseable
    {
        private final Thread thread;

        private Ticker(
            long periodMillis,
            long stepMillis)
        {
            this.thread = new Thread(() -> run(periodMillis, stepMillis), "test-clock-ticker");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        @Override
        public void close() throws InterruptedException
        {
            thread.interrupt();
            thread.join();
        }

        private void run(
            long periodMillis,
            long stepMillis)
        {
            try
            {
                while (!Thread.currentThread().isInterrupted())
                {
                    Thread.sleep(periodMillis);
                    advanceBy(stepMillis);
                }
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }
    }
}
