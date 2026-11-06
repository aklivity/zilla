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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;

public class AllocationMeterTest
{
    private static final int ARRAYS = 1_000;
    private static final int ARRAY_LENGTH = 1_024;
    private static final long EXPECTED = (long) ARRAYS * ARRAY_LENGTH;

    private volatile byte[] retained;

    @Test
    public void shouldMeasureAllocationOnTargetThread() throws Exception
    {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch allocate = new CountDownLatch(1);
        final CountDownLatch allocated = new CountDownLatch(1);
        final CountDownLatch measured = new CountDownLatch(1);

        final Thread target = new Thread(() ->
        {
            try
            {
                started.countDown();
                allocate.await();

                for (int i = 0; i < ARRAYS; i++)
                {
                    retained = new byte[ARRAY_LENGTH];
                }

                allocated.countDown();
                measured.await();
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        });
        target.start();
        started.await();

        final AllocationMeter meter = new AllocationMeter(target.threadId());
        meter.mark();

        allocate.countDown();
        allocated.await();

        final long bytes = meter.sinceMark();
        measured.countDown();
        target.join();

        assertThat(bytes, greaterThanOrEqualTo(EXPECTED));
        assertThat(bytes, lessThan(EXPECTED + EXPECTED / 10));
    }
}
