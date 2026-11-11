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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;

public class TestStoreHandlerTest
{
    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();

    @Test
    public void shouldNotAssertOperationsWithoutAssertions()
    {
        TestStoreHandler handler = newHandler(null);

        handler.put("key", "value", null, v -> {});
        handler.delete("key", v -> {});

        assertNull(entries.get("key"));
    }

    @Test
    public void shouldAcceptOperationsMatchingAssertionsInOrder()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            new Assertion("put", "key", "value", Duration.ofMinutes(1)),
            new Assertion("putIfAbsent", "other", "first", null),
            new Assertion("getAndDelete", "other", null, null),
            new Assertion("delete", "key", null, null))));

        handler.put("key", "value", Duration.ofMinutes(1), v -> {});
        handler.putIfAbsent("other", "first", null, v -> {});
        handler.getAndDelete("other", v -> {});
        handler.delete("key", v -> {});

        assertNull(entries.get("key"));
    }

    @Test
    public void shouldCompleteMatchedOperationLater()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            new Assertion("put", "key", "value", null))));
        List<String> completions = new ArrayList<>();

        handler.put("key", "value", null, completions::add);

        assertEquals(0, completions.size());
        tasks.forEach(Runnable::run);
        assertEquals(1, completions.size());
        assertEquals("value", entries.get("key").value());
    }

    @Test
    public void shouldNotAssertReads()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>());
        List<String> values = new ArrayList<>();

        handler.get("key", (k, v) -> values.add(v));
        tasks.forEach(Runnable::run);

        assertEquals(1, values.size());
    }

    @Test
    public void shouldRejectOperationWithUnexpectedValue()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            new Assertion("put", "key", "value", null))));

        AssertionError error = assertThrows(AssertionError.class, () -> handler.put("key", "other", null, v -> {}));

        assertEquals("expected store operation put key value=value ttl=null, was put key value=other ttl=null",
            error.getMessage());
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldRejectOperationWithUnexpectedTtl()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            new Assertion("put", "key", "value", Duration.ofMinutes(1)))));

        assertThrows(AssertionError.class, () -> handler.put("key", "value", null, v -> {}));
    }

    @Test
    public void shouldRejectOperationWithUnexpectedKind()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            new Assertion("put", "key", "value", null))));

        assertThrows(AssertionError.class, () -> handler.delete("key", v -> {}));
    }

    @Test
    public void shouldRejectOperationWhenNoneExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>());

        AssertionError error = assertThrows(AssertionError.class, () -> handler.put("key", "value", null, v -> {}));

        assertEquals("unexpected store operation put key", error.getMessage());
    }

    private TestStoreHandler newHandler(
        Queue<Assertion> assertions)
    {
        return new TestStoreHandler(null, tasks::add, entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            assertions);
    }
}
