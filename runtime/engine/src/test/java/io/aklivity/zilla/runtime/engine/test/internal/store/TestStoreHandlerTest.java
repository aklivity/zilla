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

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;

public class TestStoreHandlerTest
{
    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> scheduledTimes = new ArrayList<>();
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<TestExpirer> expirers = new CopyOnWriteArrayList<>();
    private final List<String> expired = new ArrayList<>();
    private long now = 1_000L;

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

    @Test
    public void shouldAcceptIncrementMatchingAssertion()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            increment("counter", 5L, "5", Duration.ofMinutes(1)),
            increment("counter", 3L, "8", null))));

        handler.increment("counter", 5L, Duration.ofMinutes(1), (k, v) -> {});
        handler.increment("counter", 3L, null, (k, v) -> {});

        assertEquals("8", entries.get("counter").value());
    }

    @Test
    public void shouldAcceptIncrementWithoutExpectedValue()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            increment("counter", 5L, null, null))));

        handler.increment("counter", 5L, null, (k, v) -> {});

        assertEquals("5", entries.get("counter").value());
    }

    @Test
    public void shouldRejectIncrementWithUnexpectedDelta()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            increment("counter", 5L, null, null))));

        AssertionError error = assertThrows(AssertionError.class, () -> handler.increment("counter", 2L, null, (k, v) -> {}));

        assertEquals("expected store operation increment counter delta=5 value=null ttl=null, " +
            "was increment counter delta=2 value=2 ttl=null", error.getMessage());
    }

    @Test
    public void shouldRejectIncrementWithUnexpectedValue()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            increment("counter", 1L, "2", null))));

        assertThrows(AssertionError.class, () -> handler.increment("counter", 1L, null, (k, v) -> {}));
    }

    @Test
    public void shouldRejectIncrementWithUnexpectedTtl()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            increment("counter", 1L, null, Duration.ofMinutes(1)))));

        assertThrows(AssertionError.class, () -> handler.increment("counter", 1L, null, (k, v) -> {}));
    }

    @Test
    public void shouldAcceptReplaceMatchingAssertion()
    {
        entries.put("key", new TestEntry("old", Long.MAX_VALUE));
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            replace("key", "old", "new", Duration.ofMinutes(1)))));
        List<Boolean> replaced = new ArrayList<>();

        handler.replace("key", "old", "new", Duration.ofMinutes(1), replaced::add);
        tasks.forEach(Runnable::run);

        assertEquals(List.of(true), replaced);
        assertEquals("new", entries.get("key").value());
    }

    @Test
    public void shouldAcceptReplaceNotApplied()
    {
        entries.put("key", new TestEntry("current", Long.MAX_VALUE));
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            replace("key", "old", "new", null))));
        List<Boolean> replaced = new ArrayList<>();

        handler.replace("key", "old", "new", null, replaced::add);
        tasks.forEach(Runnable::run);

        assertEquals(List.of(false), replaced);
        assertEquals("current", entries.get("key").value());
    }

    @Test
    public void shouldRejectReplaceWithUnexpectedExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            replace("key", "old", "new", null))));

        AssertionError error = assertThrows(AssertionError.class,
            () -> handler.replace("key", "stale", "new", null, v -> {}));

        assertEquals("expected store operation replace key expected=old value=new ttl=null, " +
            "was replace key expected=stale value=new ttl=null", error.getMessage());
    }

    @Test
    public void shouldRejectReplaceWithUnexpectedValue()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            replace("key", "old", "new", null))));

        assertThrows(AssertionError.class, () -> handler.replace("key", "old", "other", null, v -> {}));
    }

    @Test
    public void shouldRejectReplaceWithUnexpectedTtl()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            replace("key", "old", "new", Duration.ofMinutes(1)))));

        assertThrows(AssertionError.class, () -> handler.replace("key", "old", "new", null, v -> {}));
    }

    @Test
    public void shouldAcceptDeleteKeysMatchingAssertion()
    {
        entries.put("a", new TestEntry("1", Long.MAX_VALUE));
        entries.put("b", new TestEntry("2", Long.MAX_VALUE));
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            deleteKeys("a", "missing", "b"))));
        List<Long> removed = new ArrayList<>();

        handler.delete(List.of("a", "missing", "b"), removed::add);
        tasks.forEach(Runnable::run);

        assertEquals(List.of(2L), removed);
        assertNull(entries.get("a"));
        assertNull(entries.get("b"));
    }

    @Test
    public void shouldRejectDeleteKeysWithUnexpectedKeys()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            deleteKeys("a", "b"))));

        AssertionError error = assertThrows(AssertionError.class, () -> handler.delete(List.of("b", "a"), v -> {}));

        assertEquals("expected store operation delete [a, b] value=null ttl=null, " +
            "was delete [b, a] value=null ttl=null", error.getMessage());
    }

    @Test
    public void shouldRejectDeleteKeysWhenDeleteKeyExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            new Assertion("delete", "a", null, null))));

        assertThrows(AssertionError.class, () -> handler.delete(List.of("a"), v -> {}));
    }

    @Test
    public void shouldRejectDeleteKeyWhenDeleteKeysExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>(List.of(
            deleteKeys("a"))));

        assertThrows(AssertionError.class, () -> handler.delete("a", v -> {}));
    }

    @Test
    public void shouldRejectDeleteKeysWhenNoneExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>());

        AssertionError error = assertThrows(AssertionError.class, () -> handler.delete(List.of("a", "b"), v -> {}));

        assertEquals("unexpected store operation delete [a, b]", error.getMessage());
    }

    @Test
    public void shouldRejectIncrementWhenNoneExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>());

        AssertionError error = assertThrows(AssertionError.class, () -> handler.increment("counter", 1L, null, (k, v) -> {}));

        assertEquals("unexpected store operation increment counter", error.getMessage());
    }

    @Test
    public void shouldRejectReplaceWhenNoneExpected()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>());

        AssertionError error = assertThrows(AssertionError.class, () -> handler.replace("key", "old", "new", null, v -> {}));

        assertEquals("unexpected store operation replace key", error.getMessage());
    }

    @Test
    public void shouldNotAssertMultiKeyReads()
    {
        TestStoreHandler handler = newHandler(new ConcurrentLinkedQueue<>());
        List<List<String>> values = new ArrayList<>();

        handler.get(List.of("a", "b"), values::add);
        tasks.forEach(Runnable::run);

        assertEquals(1, values.size());
    }

    @Test
    public void shouldExpirePutEntry() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));

        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_001L);

        assertEquals(List.of("key=value"), expired);
        assertNull(live(handler, "key"));
    }

    @Test
    public void shouldExpirePutIfAbsentEntry() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));

        handler.putIfAbsent("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_001L);

        assertEquals(List.of("key=value"), expired);
    }

    @Test
    public void shouldNotExpireEntryWithoutTtl() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("", (key, value) -> expired.add(key));

        handler.put("key", "value", null, v -> {});
        advance(1_000_000L);

        assertEquals(List.of(), expired);
        assertEquals("value", live(handler, "key"));
    }

    @Test
    public void shouldNotExpireBeforeExpiryInstant() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key));

        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_000L);

        assertEquals(List.of(), expired);
        assertEquals("value", live(handler, "key"));
    }

    @Test
    public void shouldNotReportReplacedEntry() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));

        handler.put("key", "first", Duration.ofSeconds(1), v -> {});
        handler.put("key", "second", null, v -> {});
        advance(1_001L);

        assertEquals(List.of(), expired);
        assertEquals("second", live(handler, "key"));
    }

    @Test
    public void shouldNotReportDeletedEntry() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key));

        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        handler.delete("key", v -> {});
        advance(1_001L);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldRestartCountdownOnReplacement() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));

        handler.put("key", "first", Duration.ofSeconds(1), v -> {});
        advance(500L);
        handler.put("key", "second", Duration.ofSeconds(1), v -> {});
        advance(501L);
        assertEquals(List.of(), expired);

        advance(500L);
        assertEquals(List.of("key=second"), expired);
    }

    @Test
    public void shouldReportExpiryToMatchingPrefixOnly() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("session.", (key, value) -> expired.add("session:" + key));
        handler.expire("token.", (key, value) -> expired.add("token:" + key));

        handler.put("session.1", "a", Duration.ofSeconds(1), v -> {});
        handler.put("other", "b", Duration.ofSeconds(1), v -> {});
        advance(1_001L);

        assertEquals(List.of("session:session.1"), expired);
    }

    @Test
    public void shouldReportExpiryToEveryMatchingRegistrant() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("", (key, value) -> expired.add("all:" + key));
        handler.expire("key", (key, value) -> expired.add("key:" + key));

        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_001L);

        assertEquals(List.of("all:key", "key:key"), expired);
    }

    @Test
    public void shouldNotInvokeClosedRegistrant() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        Closeable registration = handler.expire("key", (key, value) -> expired.add(key));

        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        now += 1_001L;
        runScheduled();
        registration.close();
        tasks.forEach(Runnable::run);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldNotInvokeRegistrantsClosedOnDetach() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key));

        handler.unexpireAll();
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_001L);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldReportExpiryToLateRegistrant() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();

        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_001L);
        assertEquals(List.of(), expired);

        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        tasks.forEach(Runnable::run);

        assertEquals(List.of("key=value"), expired);
    }

    @Test
    public void shouldReportExpiryBeforeWriteOverExpiredEntry() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));

        handler.put("key", "old", Duration.ofSeconds(1), v -> {});
        now += 1_001L;
        handler.put("key", "new", null, v -> {});
        tasks.forEach(Runnable::run);

        assertEquals(List.of("key=old"), expired);
        assertEquals("new", live(handler, "key"));
    }

    @Test
    public void shouldExpireCounterEntry() throws Exception
    {
        TestStoreHandler handler = newExpiringHandler();
        handler.expire("count", (key, value) -> expired.add(key + "=" + value));

        handler.increment("count", 5L, Duration.ofSeconds(1), (key, value) -> {});
        advance(1_001L);

        assertEquals(List.of("count=5"), expired);
    }

    @Test
    public void shouldNotLoseExpiryWhenRegistrantsAttachToSharedStore() throws Exception
    {
        TestStoreHandler writer = newExpiringHandler();
        TestStoreHandler observer = newExpiringHandler();
        observer.expire("key", (key, value) -> expired.add(key + "=" + value));

        writer.put("key", "value", Duration.ofSeconds(1), v -> {});
        advance(1_001L);

        assertEquals(List.of("key=value"), expired);
    }

    private TestStoreHandler newExpiringHandler()
    {
        return new TestStoreHandler(null, tasks::add, () -> now, (time, task) ->
        {
            scheduledTimes.add(time);
            scheduledTasks.add(task);
        }, entries, new ConcurrentHashMap<>(), expirers, new ConcurrentHashMap<>(), null);
    }

    private void advance(
        long millis)
    {
        now += millis;
        runScheduled();
        tasks.forEach(Runnable::run);
        tasks.clear();
    }

    private void runScheduled()
    {
        for (int index = 0; index < scheduledTasks.size(); index++)
        {
            if (scheduledTimes.get(index) <= now)
            {
                scheduledTasks.get(index).run();
                scheduledTimes.set(index, Long.MAX_VALUE);
            }
        }
    }

    private String live(
        TestStoreHandler handler,
        String key)
    {
        List<String> values = new ArrayList<>();
        handler.get(key, (k, v) -> values.add(v));
        tasks.forEach(Runnable::run);
        tasks.clear();
        return values.get(0);
    }

    private TestStoreHandler newHandler(
        Queue<Assertion> assertions)
    {
        return new TestStoreHandler(null, tasks::add, entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            assertions);
    }

    private static Assertion increment(
        String key,
        long delta,
        String value,
        Duration ttl)
    {
        return new Assertion("increment", key, null, value, null, delta, ttl);
    }

    private static Assertion replace(
        String key,
        String expected,
        String value,
        Duration ttl)
    {
        return new Assertion("replace", key, null, value, expected, 0L, ttl);
    }

    private static Assertion deleteKeys(
        String... keys)
    {
        return new Assertion("delete", null, List.of(keys), null, null, 0L, null);
    }
}
