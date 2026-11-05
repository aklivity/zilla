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
package io.aklivity.zilla.runtime.store.memory.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class MemoryStoreHandlerTest
{
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private StoreHandler store;

    @Before
    public void init()
    {
        store = new MemoryStoreHandler(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            tasks::add);
    }

    @Test
    public void shouldIncrementAbsentKeyFromZero()
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
    public void shouldKeepTtlFromCreationOnIncrement() throws Exception
    {
        store.increment("counter", 1L, Duration.ofMillis(20), (k, v) -> {});
        store.increment("counter", 1L, Duration.ofSeconds(60), (k, v) -> {});
        drain();
        Thread.sleep(40L);

        assertThat(read("counter"), nullValue());
    }

    @Test
    public void shouldGetMultipleKeysInOrder()
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
}
