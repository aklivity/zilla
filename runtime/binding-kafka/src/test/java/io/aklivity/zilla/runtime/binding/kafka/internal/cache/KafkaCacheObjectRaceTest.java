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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class KafkaCacheObjectRaceTest
{
    private static final int ITERATIONS = 20_000;
    private static final int ACQUIRERS = 3;

    @Test(timeout = 60_000L)
    public void shouldNeverAcquireObjectWhoseCloseActionHasRun() throws Exception
    {
        final AtomicLong acquiredAfterClose = new AtomicLong();
        final AtomicLong closedMoreThanOnce = new AtomicLong();
        final AtomicLong acquired = new AtomicLong();
        final AtomicLong releasedPastClosed = new AtomicLong();
        final CyclicBarrier start = new CyclicBarrier(ACQUIRERS + 1);
        final CyclicBarrier end = new CyclicBarrier(ACQUIRERS + 1);
        final RaceObject[] current = new RaceObject[1];
        final ExecutorService executor = Executors.newFixedThreadPool(ACQUIRERS);

        try
        {
            final List<Future<?>> workers = new ArrayList<>();
            for (int t = 0; t < ACQUIRERS; t++)
            {
                workers.add(executor.submit(() ->
                {
                    for (int i = 0; i < ITERATIONS; i++)
                    {
                        start.await(10, SECONDS);
                        final RaceObject object = current[0];
                        while (!object.closed())
                        {
                            final RaceObject held = object.acquire();
                            if (held != null)
                            {
                                acquired.incrementAndGet();
                                if (held.closeActions.get() != 0)
                                {
                                    acquiredAfterClose.incrementAndGet();
                                }
                                try
                                {
                                    held.release();
                                }
                                catch (AssertionError ex)
                                {
                                    releasedPastClosed.incrementAndGet();
                                }
                            }
                        }
                        end.await(10, SECONDS);
                    }
                    return null;
                }));
            }

            for (int i = 0; i < ITERATIONS; i++)
            {
                final RaceObject object = new RaceObject();
                current[0] = object;
                start.await(10, SECONDS);
                for (int spin = i & 0x3f; spin > 0; spin--)
                {
                    Thread.onSpinWait();
                }
                try
                {
                    object.close();
                }
                catch (AssertionError ex)
                {
                    releasedPastClosed.incrementAndGet();
                }
                end.await(10, SECONDS);
                if (object.closeActions.get() > 1)
                {
                    closedMoreThanOnce.incrementAndGet();
                }
            }

            for (Future<?> worker : workers)
            {
                worker.get(30, SECONDS);
            }
        }
        finally
        {
            executor.shutdownNow();
        }

        System.out.format("RACE iterations=%d acquired=%d acquiredAfterClose=%d closedMoreThanOnce=%d releasedPastClosed=%d%n",
            ITERATIONS, acquired.get(), acquiredAfterClose.get(), closedMoreThanOnce.get(), releasedPastClosed.get());
        assertEquals("acquired an object whose close action had already run", 0L, acquiredAfterClose.get());
        assertEquals("close action ran more than once", 0L, closedMoreThanOnce.get());
        assertEquals("released an object past its final release", 0L, releasedPastClosed.get());
    }

    private static final class RaceObject extends KafkaCacheObject<RaceObject>
    {
        private final AtomicInteger closeActions = new AtomicInteger();

        @Override
        protected RaceObject self()
        {
            return this;
        }

        @Override
        protected void onClosed()
        {
            closeActions.incrementAndGet();
        }
    }
}
