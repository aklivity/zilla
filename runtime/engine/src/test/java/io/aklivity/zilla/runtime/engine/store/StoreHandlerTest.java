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
package io.aklivity.zilla.runtime.engine.store;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

public class StoreHandlerTest
{
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    private StoreHandler store;

    @Before
    public void init()
    {
        store = new TestStoreHandler(null, tasks::add,
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
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
        drain();
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
    public void shouldIncrementWhenLockIsHeldUntilReleased()
    {
        final AtomicReference<String> token = new AtomicReference<>();
        store.lock("counter", Duration.ofMillis(50), (k, t) -> token.set(t));
        drain();
        final AtomicLong result = new AtomicLong();

        store.increment("counter", 1L, null, (k, v) -> result.set(v));
        store.unlock("counter", token.get(), t -> {});
        drain();

        assertThat(result.get(), equalTo(1L));
    }

    @Test
    public void shouldGetMultipleKeysInOrder()
    {
        store.put("a", "1", null, v -> {});
        store.put("c", "3", null, v -> {});
        drain();
        final AtomicReference<List<String>> result = new AtomicReference<>();

        store.get(List.of("c", "b", "a"), result::set);

        assertThat(result.get(), nullValue());
        drain();
        assertThat(result.get(), contains("3", null, "1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectGetWithNoKeys()
    {
        store.get(List.of(), v -> {});
    }

    @Test
    public void shouldDeleteMultipleKeysAndCountRemoved()
    {
        store.put("a", "1", null, v -> {});
        store.put("b", "2", null, v -> {});
        drain();
        final AtomicLong result = new AtomicLong(-1L);

        store.delete(List.of("a", "missing", "b"), result::set);

        assertThat(result.get(), equalTo(-1L));
        drain();
        assertThat(result.get(), equalTo(2L));
        assertThat(read("a"), nullValue());
        assertThat(read("b"), nullValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectDeleteWithNoKeys()
    {
        store.delete(List.of(), v -> {});
    }

    private String read(
        String key)
    {
        final List<String> values = new ArrayList<>();
        store.get(key, (k, v) -> values.add(v));
        drain();
        assertThat(values, hasSize(1));
        return values.get(0);
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
