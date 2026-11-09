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
package io.aklivity.zilla.runtime.engine.test.internal.store;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class TestStoreHandlerTest
{
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    private StoreHandler store;

    @Before
    public void init()
    {
        store = new TestStoreHandler(null, tasks::add, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());
    }

    @Test
    public void shouldIncrementAbsentKeyFromZeroAndStoreDecimalString()
    {
        final AtomicLong result = new AtomicLong(Long.MIN_VALUE);

        store.increment("counter", 5L, Duration.ofSeconds(10), (k, v) -> result.set(v));

        assertThat(result.get(), equalTo(Long.MIN_VALUE));
        drain();
        assertThat(result.get(), equalTo(5L));
        assertThat(read("counter"), equalTo("5"));
    }

    @Test
    public void shouldIncrementExistingKey()
    {
        store.put("counter", "7", null, v -> {});
        final AtomicLong result = new AtomicLong();

        store.increment("counter", 3L, null, (k, v) -> result.set(v));
        drain();

        assertThat(result.get(), equalTo(10L));
        assertThat(read("counter"), equalTo("10"));
    }

    @Test
    public void shouldIncrementByNegativeDelta()
    {
        final AtomicLong result = new AtomicLong();

        store.increment("counter", 4L, null, (k, v) -> {});
        store.increment("counter", -6L, null, (k, v) -> result.set(v));
        drain();

        assertThat(result.get(), equalTo(-2L));
    }

    @Test
    public void shouldIncrementExpiredKeyFromZero() throws Exception
    {
        store.increment("counter", 5L, Duration.ofMillis(1), (k, v) -> {});
        drain();
        Thread.sleep(10L);
        final AtomicLong result = new AtomicLong();

        store.increment("counter", 2L, null, (k, v) -> result.set(v));
        drain();

        assertThat(result.get(), equalTo(2L));
    }

    @Test
    public void shouldApplyTtlOnCreationOnlyWhenIncrementing() throws Exception
    {
        store.increment("counter", 1L, Duration.ofMillis(20), (k, v) -> {});
        store.increment("counter", 1L, Duration.ofSeconds(60), (k, v) -> {});
        drain();
        Thread.sleep(40L);

        assertThat(read("counter"), nullValue());
    }

    @Test
    public void shouldIncrementConcurrentlyWithoutLostUpdates() throws Exception
    {
        final int callers = 8;
        final int rounds = 200;
        final CountDownLatch start = new CountDownLatch(1);
        final Thread[] threads = new Thread[callers];
        for (int caller = 0; caller < callers; caller++)
        {
            threads[caller] = new Thread(() ->
            {
                await(start);
                for (int round = 0; round < rounds; round++)
                {
                    store.increment("counter", 1L, null, (k, v) -> {});
                }
            });
            threads[caller].start();
        }
        start.countDown();
        for (Thread thread : threads)
        {
            thread.join();
        }
        drain();

        assertThat(read("counter"), equalTo(Integer.toString(callers * rounds)));
    }

    @Test
    public void shouldGetMultipleKeysInOrderWithNullForAbsent()
    {
        store.put("a", "1", null, v -> {});
        store.put("c", "3", null, v -> {});
        final AtomicReference<List<String>> result = new AtomicReference<>();

        store.get(List.of("c", "b", "a"), result::set);

        assertThat(result.get(), nullValue());
        drain();
        assertThat(result.get(), contains("3", null, "1"));
    }

    @Test
    public void shouldGetNullForExpiredKeyInMultipleKeys() throws Exception
    {
        store.put("a", "1", Duration.ofMillis(1), v -> {});
        store.put("b", "2", null, v -> {});
        drain();
        Thread.sleep(10L);
        final AtomicReference<List<String>> result = new AtomicReference<>();

        store.get(List.of("a", "b"), result::set);
        drain();

        assertThat(result.get(), contains(null, "2"));
    }

    @Test
    public void shouldDeleteMultipleKeysAndCountRemoved()
    {
        store.put("a", "1", null, v -> {});
        store.put("b", "2", null, v -> {});
        final AtomicLong result = new AtomicLong(-1L);

        store.delete(List.of("a", "missing", "b"), result::set);

        assertThat(result.get(), equalTo(-1L));
        drain();
        assertThat(result.get(), equalTo(2L));
        assertThat(read("a"), nullValue());
        assertThat(read("b"), nullValue());
    }

    @Test
    public void shouldNotCountExpiredKeysAsDeleted() throws Exception
    {
        store.put("a", "1", Duration.ofMillis(1), v -> {});
        store.put("b", "2", null, v -> {});
        drain();
        Thread.sleep(10L);
        final AtomicLong result = new AtomicLong(-1L);

        store.delete(List.of("a", "b"), result::set);
        drain();

        assertThat(result.get(), equalTo(1L));
    }

    @Test
    public void shouldReplaceWhenValueMatches()
    {
        store.put("key", "old", null, v -> {});
        final AtomicReference<Boolean> result = new AtomicReference<>();

        store.replace("key", "old", "new", null, result::set);

        assertThat(result.get(), nullValue());
        drain();
        assertThat(result.get(), equalTo(true));
        assertThat(read("key"), equalTo("new"));
    }

    @Test
    public void shouldNotReplaceWhenValueDiffers()
    {
        store.put("key", "old", null, v -> {});
        final AtomicReference<Boolean> result = new AtomicReference<>();

        store.replace("key", "other", "new", null, result::set);
        drain();

        assertThat(result.get(), equalTo(false));
        assertThat(read("key"), equalTo("old"));
    }

    @Test
    public void shouldNotReplaceAbsentKey()
    {
        final AtomicReference<Boolean> result = new AtomicReference<>();

        store.replace("key", "old", "new", null, result::set);
        drain();

        assertThat(result.get(), equalTo(false));
        assertThat(read("key"), nullValue());
    }

    @Test
    public void shouldNotReplaceExpiredKey() throws Exception
    {
        store.put("key", "old", Duration.ofMillis(1), v -> {});
        drain();
        Thread.sleep(10L);
        final AtomicReference<Boolean> result = new AtomicReference<>();

        store.replace("key", "old", "new", null, result::set);
        drain();

        assertThat(result.get(), equalTo(false));
        assertThat(read("key"), nullValue());
    }

    @Test
    public void shouldReplaceWithoutExpiryWhenTtlIsNull() throws Exception
    {
        store.put("key", "old", Duration.ofMillis(20), v -> {});
        drain();
        store.replace("key", "old", "new", null, v -> {});
        drain();
        Thread.sleep(40L);

        assertThat(read("key"), equalTo("new"));
    }

    @Test
    public void shouldApplyTtlToReplacedValueAndExpire() throws Exception
    {
        store.put("key", "old", null, v -> {});
        store.replace("key", "old", "new", Duration.ofMillis(20), v -> {});
        drain();

        assertThat(read("key"), equalTo("new"));
        Thread.sleep(40L);
        assertThat(read("key"), nullValue());
    }

    @Test
    public void shouldReplaceExactlyOnceAmongConcurrentCallers() throws Exception
    {
        final int callers = 8;
        store.put("key", "old", null, v -> {});
        drain();
        final AtomicInteger winners = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final Thread[] threads = new Thread[callers];
        for (int caller = 0; caller < callers; caller++)
        {
            final String value = "new" + caller;
            threads[caller] = new Thread(() ->
            {
                await(start);
                store.replace("key", "old", value, null, replaced -> winners.addAndGet(replaced ? 1 : 0));
            });
            threads[caller].start();
        }
        start.countDown();
        for (Thread thread : threads)
        {
            thread.join();
        }
        drain();

        assertThat(winners.get(), equalTo(1));
    }

    @Test
    public void shouldCompleteStrictlyLaterForEveryOperation()
    {
        final AtomicBoolean completed = new AtomicBoolean();

        store.increment("counter", 1L, null, (k, v) -> completed.set(true));
        store.get(List.of("counter"), v -> completed.set(true));
        store.delete(List.of("counter"), v -> completed.set(true));
        store.replace("counter", "1", "2", null, v -> completed.set(true));

        assertThat(completed.get(), equalTo(false));
        drain();
        assertThat(completed.get(), equalTo(true));
    }

    private String read(
        String key)
    {
        final AtomicReference<String> value = new AtomicReference<>();
        store.get(key, (k, v) -> value.set(v));
        drain();
        return value.get();
    }

    private void drain()
    {
        Runnable task;
        while ((task = tasks.poll()) != null)
        {
            task.run();
        }
    }

    private static void await(
        CountDownLatch latch)
    {
        try
        {
            latch.await();
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
