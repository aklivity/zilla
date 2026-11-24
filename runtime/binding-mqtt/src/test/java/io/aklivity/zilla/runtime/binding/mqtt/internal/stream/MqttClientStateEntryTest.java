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
package io.aklivity.zilla.runtime.binding.mqtt.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestEntry;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

public class MqttClientStateEntryTest
{
    private static final String KEY = "client#state";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final MqttMembershipRecord MEMBERSHIP = new MqttMembershipRecord("consumers", "sensor/one");

    private final MqttClientStates states = new MqttClientStates();
    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private int completions;

    @Test
    public void shouldHoldAbsentRecordWithPutIfAbsent()
    {
        final String held = encode("replica-a");
        final MqttClientStateEntry entry = newEntry(new Assertion("putIfAbsent", KEY, held, TTL));
        runTasks();

        entry.hold("replica-a", this::completed);
        runTasks();

        assertEquals(1, completions);
        assertEquals(held, stored());
    }

    @Test
    public void shouldHoldStoredRecordWithReplace()
    {
        final String previous = encode("replica-z");
        final String held = encode("replica-a");
        entries.put(KEY, new TestEntry(previous, Long.MAX_VALUE));
        final MqttClientStateEntry entry = newEntry(replace(previous, held));
        runTasks();

        entry.hold("replica-a", this::completed);
        runTasks();

        assertEquals(1, completions);
        assertEquals(held, stored());
    }

    @Test
    public void shouldReleaseHeldRecordWithReplace()
    {
        final String held = encode("replica-a");
        final String released = encode(null);
        final MqttClientStateEntry entry = newEntry(new Assertion("putIfAbsent", KEY, held, TTL), replace(held, released));
        runTasks();
        entry.hold("replica-a", this::completed);
        runTasks();

        entry.release(this::completed);
        runTasks();

        assertEquals(2, completions);
        assertEquals(released, stored());
        assertNull(states.decode(stored()).holder);
    }

    @Test
    public void shouldWriteSuccessiveChangesAgainstLatestStoredValue()
    {
        final String held = encode("replica-a");
        final String shared = encode("replica-a", MEMBERSHIP);
        final String released = encode(null, MEMBERSHIP);
        final MqttClientStateEntry entry = newEntry(
            new Assertion("putIfAbsent", KEY, held, TTL), replace(held, shared), replace(shared, released));
        runTasks();
        entry.hold("replica-a", this::completed);
        runTasks();
        entry.memberships().add(MEMBERSHIP);
        entry.write(this::completed);
        runTasks();

        entry.release(this::completed);
        runTasks();

        assertEquals(3, completions);
        assertEquals(released, stored());
    }

    @Test
    public void shouldDropReleaseWhenRecordWasReplacedByNewOwner()
    {
        final String held = encode("replica-a");
        final String takenOver = encode("replica-b");
        final MqttClientStateEntry entry = newEntry(new Assertion("putIfAbsent", KEY, held, TTL), replace(held, encode(null)));
        runTasks();
        entry.hold("replica-a", this::completed);
        runTasks();
        entries.put(KEY, new TestEntry(takenOver, Long.MAX_VALUE));

        entry.release(this::completed);
        runTasks();

        assertEquals(2, completions);
        assertEquals(takenOver, stored());
        assertEquals("replica-b", states.decode(stored()).holder);
    }

    @Test
    public void shouldNotResurrectRecordDiscardedByNewOwner()
    {
        final String held = encode("replica-a");
        final MqttClientStateEntry entry = newEntry(new Assertion("putIfAbsent", KEY, held, TTL), replace(held, encode(null)));
        runTasks();
        entry.hold("replica-a", this::completed);
        runTasks();
        entries.remove(KEY);

        entry.release(this::completed);
        runTasks();

        assertEquals(2, completions);
        assertNull(entries.get(KEY));
    }

    @Test
    public void shouldDropEveryWriteAfterSuperseded()
    {
        final String held = encode("replica-a");
        final String takenOver = encode("replica-b");
        final MqttClientStateEntry entry = newEntry(new Assertion("putIfAbsent", KEY, held, TTL), replace(held, encode(null)));
        runTasks();
        entry.hold("replica-a", this::completed);
        runTasks();
        entries.put(KEY, new TestEntry(takenOver, Long.MAX_VALUE));
        entry.release(this::completed);
        runTasks();

        entry.memberships().add(MEMBERSHIP);
        entry.write(this::completed);
        runTasks();

        assertEquals(3, completions);
        assertEquals(takenOver, stored());
    }

    @Test
    public void shouldDeleteEmptyRecordWithoutHold()
    {
        final String shared = encode(null, MEMBERSHIP);
        final MqttClientStateEntry entry = newEntry(
            new Assertion("put", KEY, shared, TTL), new Assertion("delete", KEY, null, null));
        runTasks();
        entry.memberships().add(MEMBERSHIP);
        entry.write(this::completed);
        runTasks();

        entry.discard();
        entry.write(this::completed);
        runTasks();

        assertEquals(2, completions);
        assertNull(entries.get(KEY));
    }

    @Test
    public void shouldReportHeldRecordLoaded()
    {
        entries.put(KEY, new TestEntry(encode("replica-z"), Long.MAX_VALUE));
        final MqttClientStateEntry entry = newEntry();
        runTasks();

        assertTrue(entry.ready());
        assertTrue(entry.held());
    }

    private MqttClientStateEntry newEntry(
        Assertion... assertions)
    {
        final Queue<Assertion> expected = new ConcurrentLinkedQueue<>(List.of(assertions));
        final TestStoreHandler store = new TestStoreHandler(
            null, tasks::add, entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), expected);

        return new MqttClientStateEntry(store, KEY, states, () -> TTL);
    }

    private String encode(
        String holder,
        MqttMembershipRecord... memberships)
    {
        return states.encode(List.of(memberships), List.of(), holder, new byte[0], new byte[0]);
    }

    private String stored()
    {
        final TestEntry entry = entries.get(KEY);

        return entry != null ? entry.value() : null;
    }

    private void completed()
    {
        completions++;
    }

    private void runTasks()
    {
        while (!tasks.isEmpty())
        {
            final List<Runnable> batch = new ArrayList<>(tasks);
            tasks.clear();
            batch.forEach(Runnable::run);
        }
    }

    private static Assertion replace(
        String expected,
        String value)
    {
        return new Assertion("replace", KEY, null, value, expected, 0L, TTL);
    }
}
