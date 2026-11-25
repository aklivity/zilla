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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

public class KafkaCacheDoorbellTest
{
    @Rule
    public final TestRule timeout = new DisableOnDebug(new Timeout(60, SECONDS));

    @Test
    public void shouldRingWhenIdle()
    {
        KafkaCacheDoorbell doorbell = new KafkaCacheDoorbell();

        assertTrue(doorbell.ring(1L));
    }

    @Test
    public void shouldNotRingWhilePending()
    {
        KafkaCacheDoorbell doorbell = new KafkaCacheDoorbell();

        doorbell.ring(1L);

        assertFalse(doorbell.ring(2L));
    }

    @Test
    public void shouldRingAgainAfterClear()
    {
        KafkaCacheDoorbell doorbell = new KafkaCacheDoorbell();

        doorbell.ring(1L);
        doorbell.clear();

        assertTrue(doorbell.ring(2L));
    }

    @Test
    public void shouldReleaseOnlyOwnRing()
    {
        KafkaCacheDoorbell doorbell = new KafkaCacheDoorbell();

        doorbell.ring(1L);
        doorbell.release(2L);

        assertFalse(doorbell.ring(3L));

        doorbell.release(1L);

        assertTrue(doorbell.ring(3L));
    }

    @Test
    public void shouldObserveEveryPublishedOffsetWhenRingsAreCoalesced() throws Exception
    {
        final int writers = 3;
        final int offsets = 300;

        for (int run = 0; run < 200; run++)
        {
            final KafkaCacheDoorbell doorbell = new KafkaCacheDoorbell();
            final AtomicLongArray committed = new AtomicLongArray(writers);
            final Queue<Integer> rings = new ConcurrentLinkedQueue<>();
            final AtomicInteger writersDone = new AtomicInteger();
            final long[] observed = new long[writers];

            final Thread reader = new Thread(() ->
            {
                while (writersDone.get() < writers || !rings.isEmpty())
                {
                    if (rings.poll() != null)
                    {
                        doorbell.clear();
                        for (int writer = 0; writer < writers; writer++)
                        {
                            observed[writer] = committed.get(writer);
                        }
                    }
                }
            });

            final Thread[] threads = new Thread[writers];
            for (int writer = 0; writer < writers; writer++)
            {
                final int index = writer;
                threads[writer] = new Thread(() ->
                {
                    for (int offset = 1; offset <= offsets; offset++)
                    {
                        committed.set(index, offset);
                        if (doorbell.ring(index + 1L))
                        {
                            rings.add(index);
                        }
                    }
                    writersDone.incrementAndGet();
                });
            }

            reader.start();
            for (Thread thread : threads)
            {
                thread.start();
            }
            for (Thread thread : threads)
            {
                thread.join();
            }
            reader.join();

            for (int writer = 0; writer < writers; writer++)
            {
                assertEquals(offsets, observed[writer]);
            }
        }
    }
}
