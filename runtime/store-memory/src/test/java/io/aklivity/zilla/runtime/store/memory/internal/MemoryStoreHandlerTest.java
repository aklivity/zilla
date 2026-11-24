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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;

public class MemoryStoreHandlerTest
{
    private static final long INTERVAL = 1_000L;
    private static final int CHUNK = 2;

    private final ConcurrentMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final MemoryExpirers expirers = new MemoryExpirers();
    private final AtomicLong nextSweepAt = new AtomicLong();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<String> expired = new ArrayList<>();
    private final ManualClock clock = new ManualClock();

    @Test
    public void shouldSweepExpiredEntryAndReportValue() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key=value"), expired);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldNotSweepBeforeExpiryInstant() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        clock.now += 1_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertEquals("value", entries.get("key").value());
    }

    @Test
    public void shouldNotSweepEntryWithoutTtl() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("", (key, value) -> expired.add(key));
        handler.put("key", "value", null, v -> {});

        clock.now += 1_000_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertEquals("value", entries.get("key").value());
    }

    @Test
    public void shouldDropExpiredEntryWithoutRegistrant() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();

        assertNull(entries.get("key"));
    }

    @Test
    public void shouldReportExpiryToMatchingPrefixOnly() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("session.", (key, value) -> expired.add("session:" + key));
        handler.expire("token.", (key, value) -> expired.add("token:" + key));
        handler.put("session.1", "a", Duration.ofSeconds(1), v -> {});
        handler.put("other", "b", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("session:session.1"), expired);
    }

    @Test
    public void shouldNotReportReplacedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "first", Duration.ofSeconds(1), v -> {});
        handler.put("key", "second", null, v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldNotReportDeletedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        handler.delete("key", v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldReportExpiryBeforeWriteOverExpiredEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "old", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        handler.put("key", "new", null, v -> {});
        run(tasks);

        assertEquals(List.of("key=old"), expired);
        assertEquals("new", entries.get("key").value());
    }

    @Test
    public void shouldReportExpiryWhenDeletingExpiredEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "old", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        handler.delete("key", v -> {});
        run(tasks);

        assertEquals(List.of("key=old"), expired);
    }

    @Test
    public void shouldReportExpiryWhenIncrementingExpiredCounter() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("count", (key, value) -> expired.add(key + "=" + value));
        handler.increment("count", 5L, Duration.ofSeconds(1), (key, value) -> {});

        clock.now += 1_001L;
        handler.increment("count", 1L, null, (key, value) -> {});
        run(tasks);

        assertEquals(List.of("count=5"), expired);
    }

    @Test
    public void shouldNotInvokeClosedRegistrant() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        Closeable registration = handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        registration.close();
        run(tasks);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldNotInvokeRegistrantsClosedOnDetach() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        handler.unexpireAll();
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldSweepExpiredLockLease() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.lock("key", Duration.ofSeconds(1), (key, token) -> {});

        clock.now += 1_001L;
        newSweeper().sweep();

        assertTrue(locks.isEmpty());
    }

    @Test
    public void shouldSweepInChunksThenWaitForInterval() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        for (int index = 0; index < 5; index++)
        {
            handler.put("key" + index, "value", Duration.ofSeconds(1), v -> {});
        }
        MemorySweeper sweeper = newSweeper();

        clock.now += 1_001L;
        long first = sweeper.sweep();
        assertEquals(3, entries.size());
        long second = sweeper.sweep();
        assertEquals(1, entries.size());
        long third = sweeper.sweep();

        assertEquals(0, entries.size());
        assertTrue(first < INTERVAL);
        assertTrue(second < INTERVAL);
        assertEquals(INTERVAL, third);
    }

    @Test
    public void shouldNotSweepAgainBeforeInterval() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        MemorySweeper sweeper = newSweeper();
        sweeper.sweep();

        handler.put("key", "value", Duration.ofMillis(1), v -> {});
        clock.now += 500L;
        sweeper.sweep();

        assertEquals("value", entries.get("key").value());

        clock.now += 600L;
        sweeper.sweep();

        assertNull(entries.get("key"));
    }

    @Test
    public void shouldReportExpiryOnceToRegistrantOnEachWorker() throws Exception
    {
        List<Runnable> otherTasks = new ArrayList<>();
        MemoryStoreHandler first = newHandler(tasks);
        MemoryStoreHandler second = newHandler(otherTasks);
        first.expire("key", (key, value) -> expired.add("first:" + key));
        second.expire("key", (key, value) -> expired.add("second:" + key));
        first.put("key", "value", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        newSweeper().sweep();
        run(tasks);
        run(otherTasks);

        assertEquals(List.of("first:key", "second:key"), expired);
    }

    private MemoryStoreHandler newHandler(
        List<Runnable> dispatched)
    {
        return new MemoryStoreHandler(entries, new ConcurrentHashMap<>(), locks, expirers, dispatched::add, clock);
    }

    private MemorySweeper newSweeper()
    {
        return new MemorySweeper(entries, locks, expirers, nextSweepAt, clock, INTERVAL, CHUNK);
    }

    private static void run(
        List<Runnable> dispatched)
    {
        List<Runnable> pending = new ArrayList<>(dispatched);
        dispatched.clear();
        pending.forEach(Runnable::run);
    }

    private static final class ManualClock extends Clock
    {
        private long now = 10_000L;

        @Override
        public ZoneId getZone()
        {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(
            ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return Instant.ofEpochMilli(now);
        }

        @Override
        public long millis()
        {
            return now;
        }
    }
}
