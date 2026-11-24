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
import static org.junit.Assert.assertThrows;
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

import io.aklivity.zilla.runtime.engine.store.StoreHandler.Expiration;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.Watcher;

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
    public void shouldNotReportExpiryWhenIncrementingExpiredCounter() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("count", (key, value) -> expired.add(key + "=" + value));
        handler.increment("count", 5L, Duration.ofSeconds(1), (key, value) -> {});

        clock.now += 1_001L;
        handler.increment("count", 1L, null, (key, value) -> {});
        run(tasks);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldNotSweepReportExpiryOfCounter() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("count", (key, value) -> expired.add(key + "=" + value));
        handler.increment("count", 5L, Duration.ofSeconds(1), (key, value) -> {});
        handler.increment("count", 1L, null, (key, value) -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertNull(entries.get("count"));
    }

    @Test
    public void shouldNotReportExpiryOfTrackedEntryWhenIncremented() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("", (key, value) -> expired.add(key + "=" + value));
        handler.put("tracked", "5", Duration.ofSeconds(1), v -> {});
        handler.put("untracked", "5", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        handler.increment("tracked", 1L, Duration.ofHours(1), (key, value) -> {});
        handler.increment("untracked", 1L, Duration.ofHours(1), (key, value) -> {});
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertNull(entries.get("tracked"));
        assertNull(entries.get("untracked"));
    }

    @Test
    public void shouldKeepRemainingLifetimeWhenIncrementingTrackedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.put("key", "5", Duration.ofSeconds(1), v -> {});

        handler.increment("key", 1L, Duration.ofHours(1), (key, value) -> {});
        clock.now += 500L;
        newSweeper().sweep();

        assertEquals("6", entries.get("key").value());

        clock.now += 1_000L;
        newSweeper().sweep();

        assertNull(entries.get("key"));
    }

    @Test
    public void shouldNotSweepReportExpiryOfUntrackedPut() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldNotSweepReportExpiryOfUntrackedPutIfAbsent() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.putIfAbsent("key", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldNotSweepReportExpiryOfUntrackedReplace() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "seed", Duration.ofHours(1), v -> {});
        handler.replace("key", "seed", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldSweepReportTrackedSiblingOfUntrackedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key.", (key, value) -> expired.add(key + "=" + value));
        handler.put("key.untracked", "untracked-value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});
        handler.put("key.tracked", "tracked-value", Duration.ofSeconds(1), Expiration.TRACKED, v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key.tracked=tracked-value"), expired);
        assertEquals(0, entries.size());
    }

    @Test
    public void shouldKeepUntrackedWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        handler.touch("key", Duration.ofSeconds(5), v -> {});
        clock.now += 6_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldKeepTrackedWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofSeconds(1), Expiration.TRACKED, v -> {});

        handler.touch("key", Duration.ofSeconds(5), v -> {});
        clock.now += 6_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key=value"), expired);
    }

    @Test
    public void shouldTrackWhenTouchGivesExpiryToEntryWithoutOne() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", null, Expiration.UNTRACKED, v -> {});

        handler.touch("key", Duration.ofSeconds(1), v -> {});
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key=value"), expired);
    }

    @Test
    public void shouldNotExpireUntrackedPutWithoutTtl() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "value", null, Expiration.UNTRACKED, v -> {});

        clock.now += 1_000_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertEquals("value", entries.get("key").value());
    }

    @Test
    public void shouldReportExpiryOfTrackedWriteOverUntrackedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "first", Duration.ofHours(1), Expiration.UNTRACKED, v -> {});

        handler.put("key", "second", Duration.ofSeconds(1), v -> {});
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key=second"), expired);
    }

    @Test
    public void shouldNotReportExpiryOfUntrackedWriteOverTrackedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "first", Duration.ofSeconds(1), v -> {});

        handler.put("key", "second", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
    }

    @Test
    public void shouldReportExpiryOfTrackedReplaceOverUntrackedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "first", Duration.ofHours(1), Expiration.UNTRACKED, v -> {});
        handler.replace("key", "first", "second", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key=second"), expired);
    }

    @Test
    public void shouldReportExpiryBeforeUntrackedWriteOverExpiredTrackedEntry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "old", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        handler.put("key", "new", Duration.ofHours(1), Expiration.UNTRACKED, v -> {});
        run(tasks);

        assertEquals(List.of("key=old"), expired);
    }

    @Test
    public void shouldNotReportExpiryOfExpiredUntrackedEntryWhenOverwritten() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "old", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        clock.now += 1_001L;
        handler.put("key", "new", null, v -> {});
        run(tasks);

        assertEquals(List.of(), expired);
        assertEquals("new", entries.get("key").value());
    }

    @Test
    public void shouldNotReportExpiredUntrackedEntryWhenDeletedOrTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();
        handler.expire("", (key, value) -> expired.add(key + "=" + value));
        handler.put("deleted", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});
        handler.put("touched", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});

        clock.now += 1_001L;
        handler.delete("deleted", v -> {});
        handler.touch("touched", Duration.ofSeconds(5), touched::add);
        run(tasks);

        assertEquals(List.of(), expired);
        assertEquals(List.of(false), touched);
    }

    @Test
    public void shouldNotNotifyWatchersWhenUntrackedEntryExpires() throws Exception
    {
        ConcurrentMap<String, List<Watcher>> watchers = new ConcurrentHashMap<>();
        MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, expirers, tasks::add, clock);
        List<String> changed = new ArrayList<>();
        handler.put("key", "value", Duration.ofSeconds(1), Expiration.UNTRACKED, v -> {});
        handler.watch("key", (key, value) -> changed.add(key + "=" + value));
        run(tasks);

        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), changed);
    }

    @Test
    public void shouldRejectNullExpiration() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);

        assertThrows(NullPointerException.class, () -> handler.put("key", "value", null, null, v -> {}));
        assertThrows(NullPointerException.class, () -> handler.putIfAbsent("key", "value", null, null, v -> {}));
        assertThrows(NullPointerException.class, () -> handler.replace("key", "value", "next", null, null, v -> {}));
    }

    @Test
    public void shouldKeepTouchedEntryPastOriginalExpiryAndSweepAtNewExpiry() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        handler.touch("key", Duration.ofSeconds(5), touched::add);
        assertEquals(List.of(), touched);
        run(tasks);
        assertEquals(List.of(true), touched);

        clock.now += 2_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(), expired);
        assertEquals("value", entries.get("key").value());

        clock.now += 4_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of("key=value"), expired);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldShortenCountdownWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofHours(1), v -> {});

        handler.touch("key", Duration.ofSeconds(1), touched::add);
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(true), touched);
        assertEquals(List.of("key=value"), expired);
    }

    @Test
    public void shouldGiveTimeToLiveToEntryWithoutOneWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", null, v -> {});

        handler.touch("key", Duration.ofSeconds(1), touched::add);
        clock.now += 1_001L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(true), touched);
        assertEquals(List.of("key=value"), expired);
    }

    @Test
    public void shouldRemoveTimeToLiveWhenTouchedWithoutOne() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();
        handler.expire("key", (key, value) -> expired.add(key));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        handler.touch("key", null, touched::add);
        clock.now += 1_000_000L;
        newSweeper().sweep();
        run(tasks);

        assertEquals(List.of(true), touched);
        assertEquals(List.of(), expired);
        assertEquals("value", entries.get("key").value());
    }

    @Test
    public void shouldNotFindAbsentEntryWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();

        handler.touch("key", Duration.ofSeconds(1), touched::add);
        run(tasks);

        assertEquals(List.of(false), touched);
        assertNull(entries.get("key"));
    }

    @Test
    public void shouldNotNotifyWatchersWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<String> changed = new ArrayList<>();
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});
        handler.watch("key", (key, value) -> changed.add(key + "=" + value));
        run(tasks);

        handler.touch("key", Duration.ofSeconds(5), v -> {});
        run(tasks);

        assertEquals(List.of(), changed);
    }

    @Test
    public void shouldReportExpiryAndNotFindExpiredEntryWhenTouched() throws Exception
    {
        MemoryStoreHandler handler = newHandler(tasks);
        List<Boolean> touched = new ArrayList<>();
        handler.expire("key", (key, value) -> expired.add(key + "=" + value));
        handler.put("key", "value", Duration.ofSeconds(1), v -> {});

        clock.now += 1_001L;
        handler.touch("key", Duration.ofSeconds(5), touched::add);
        run(tasks);

        assertEquals(List.of(false), touched);
        assertEquals(List.of("key=value"), expired);
        assertNull(entries.get("key"));
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
