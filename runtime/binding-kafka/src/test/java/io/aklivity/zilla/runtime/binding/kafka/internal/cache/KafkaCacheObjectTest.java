/*
 * Copyright 2021-2022 Aklivity Inc.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

public class KafkaCacheObjectTest
{
    @Rule
    public final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Test
    public void shouldCloseObject() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);

        TestObject object = new TestObject(latch);

        object.close();

        latch.await();

        assert object.closed();
    }

    @Test
    public void shouldCloseObjectAfterAcquireAndRelease() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);

        TestObject object = new TestObject(latch);
        object.acquire();
        object.release();
        object.close();

        latch.await();

        assert object.closed();
    }

    @Test
    public void shouldCloseObjectAfterParallelAcquireAndRelease() throws Exception
    {
        int readerCount = 10;

        CountDownLatch latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
        try
        {
            TestObject object = new TestObject(latch);
            for (int i = 0; i < readerCount; i++)
            {
                executor.submit(() -> object.acquire().release());
            }
            object.close();

            latch.await();

            assert object.closed();
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldCloseObjectAfterSequentialAcquireAndRelease() throws Exception
    {
        int readerCount = 10;

        CountDownLatch latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        try
        {
            TestObject writer = new TestObject(latch);

            for (int i = 0; i < readerCount; i++)
            {
                executor.submit(() -> writer.acquire()).get();
            }

            for (int i = 0; i < readerCount; i++)
            {
                executor.submit(() -> writer.release()).get();
            }

            writer.close();

            latch.await();

            assert writer.closed();
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private static final class TestObject extends KafkaCacheObject<TestObject>
    {
        private final CountDownLatch latch;

        private TestObject(
            CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        protected TestObject self()
        {
            return this;
        }

        @Override
        protected void onClosed()
        {
            assert latch.getCount() > 0;
            latch.countDown();
        }
    }
}
