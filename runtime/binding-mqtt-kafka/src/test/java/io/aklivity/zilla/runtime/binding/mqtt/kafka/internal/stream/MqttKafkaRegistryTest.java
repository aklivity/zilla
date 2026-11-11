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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

public class MqttKafkaRegistryTest
{
    private static final String KEY = "zilla:test:share:consumers:c2Vuc29yL29uZQ:registry";

    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> epochs = new ArrayList<>();
    private int failures;
    private int completions;

    @Test
    public void shouldJoinFirstLifeWhenAbsent()
    {
        final MqttKafkaRegistry registry = newRegistry(putIfAbsent("0:1"));

        registry.join(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals("0:1", stored());
        assertEquals(0, failures);
    }

    @Test
    public void shouldJoinLiveSubscription()
    {
        seed("0:1");
        final MqttKafkaRegistry registry = newRegistry(replace("0:1", "0:2"));

        registry.join(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals("0:2", stored());
    }

    @Test
    public void shouldJoinLiveSubscriptionOfLaterEpoch()
    {
        seed("3:4");
        final MqttKafkaRegistry registry = newRegistry(replace("3:4", "3:5"));

        registry.join(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(3L), epochs);
        assertEquals("3:5", stored());
    }

    @Test
    public void shouldBeginNewLifeWhenNoMembers()
    {
        seed("0:0");
        final MqttKafkaRegistry registry = newRegistry(replace("0:0", "1:1"));

        registry.join(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(1L), epochs);
        assertEquals("1:1", stored());
    }

    @Test
    public void shouldBeginNewLifeAfterLaterEpoch()
    {
        seed("7:0");
        final MqttKafkaRegistry registry = newRegistry(replace("7:0", "8:1"));

        registry.join(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(8L), epochs);
        assertEquals("8:1", stored());
    }

    @Test
    public void shouldRetryJoinWhenAbsentEntryCreatedFirst()
    {
        final MqttKafkaRegistry registry = newRegistry(putIfAbsent("0:1"), replace("0:1", "0:2"));

        registry.join(KEY, epochs::add, this::failed);
        seed("0:1");
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals("0:2", stored());
    }

    @Test
    public void shouldRetryJoinWhenReplaceLosesRace()
    {
        seed("0:1");
        final MqttKafkaRegistry registry = newRegistry(replace("0:1", "0:2"), replace("0:2", "0:3"));

        registry.join(KEY, epochs::add, this::failed);
        seed("0:2");
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals("0:3", stored());
    }

    @Test
    public void shouldBeginNewLifeWhenRaceEmptiesSubscription()
    {
        seed("0:1");
        final MqttKafkaRegistry registry = newRegistry(replace("0:1", "0:2"), replace("0:0", "1:1"));

        registry.join(KEY, epochs::add, this::failed);
        seed("0:0");
        runTasks();

        assertEquals(List.of(1L), epochs);
        assertEquals("1:1", stored());
    }

    @Test
    public void shouldJoinOnLastAttempt()
    {
        seed("0:1");
        final List<Assertion> expected = new ArrayList<>();
        for (int attempt = 0; attempt < MqttKafkaRegistry.ATTEMPTS_MAX - 1; attempt++)
        {
            expected.add(replace("0:" + (attempt + 1), "0:" + (attempt + 2)));
        }
        expected.add(replace("0:" + MqttKafkaRegistry.ATTEMPTS_MAX, "0:" + (MqttKafkaRegistry.ATTEMPTS_MAX + 1)));
        final MqttKafkaRegistry registry = newRegistry(expected.toArray(Assertion[]::new));

        registry.join(KEY, epochs::add, this::failed);
        for (int attempt = 0; attempt < MqttKafkaRegistry.ATTEMPTS_MAX - 1; attempt++)
        {
            seed("0:" + (attempt + 2));
            runLostAttempt();
        }
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals("0:" + (MqttKafkaRegistry.ATTEMPTS_MAX + 1), stored());
        assertEquals(0, failures);
    }

    @Test
    public void shouldFailJoinWhenAttemptsExhausted()
    {
        seed("0:1");
        final List<Assertion> expected = new ArrayList<>();
        for (int attempt = 0; attempt < MqttKafkaRegistry.ATTEMPTS_MAX; attempt++)
        {
            expected.add(replace("0:" + (attempt + 1), "0:" + (attempt + 2)));
        }
        final MqttKafkaRegistry registry = newRegistry(expected.toArray(Assertion[]::new));

        registry.join(KEY, epochs::add, this::failed);
        for (int attempt = 0; attempt < MqttKafkaRegistry.ATTEMPTS_MAX; attempt++)
        {
            seed("0:" + (attempt + 2));
            runLostAttempt();
        }
        runTasks();

        assertTrue(epochs.isEmpty());
        assertEquals(1, failures);
        assertEquals("0:" + (MqttKafkaRegistry.ATTEMPTS_MAX + 1), stored());
    }

    @Test
    public void shouldFailJoinWhenEntryUnreadable()
    {
        for (String unreadable : List.of("", "x", "1", "1:", ":1", "a:1", "1:b", "01:1", "1:01", "-1:1", "1:-1", "1:2:3", "+1:1"))
        {
            seed(unreadable);
            final MqttKafkaRegistry registry = newRegistry();

            registry.join(KEY, epochs::add, this::failed);
            runTasks();

            assertEquals(unreadable, unreadable, stored());
        }

        assertTrue(epochs.isEmpty());
        assertEquals(13, failures);
    }

    @Test
    public void shouldFindEpochOfAbsentEntry()
    {
        final MqttKafkaRegistry registry = newRegistry();

        registry.epoch(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertNull(stored());
    }

    @Test
    public void shouldFindEpochWithoutChangingEntry()
    {
        seed("4:2");
        final MqttKafkaRegistry registry = newRegistry();

        registry.epoch(KEY, epochs::add, this::failed);
        runTasks();

        assertEquals(List.of(4L), epochs);
        assertEquals("4:2", stored());
    }

    @Test
    public void shouldFailToFindEpochWhenEntryUnreadable()
    {
        seed("x");
        final MqttKafkaRegistry registry = newRegistry();

        registry.epoch(KEY, epochs::add, this::failed);
        runTasks();

        assertTrue(epochs.isEmpty());
        assertEquals(1, failures);
    }

    @Test
    public void shouldLeaveSubscription()
    {
        seed("0:2");
        final MqttKafkaRegistry registry = newRegistry(replace("0:2", "0:1"));

        registry.leave(KEY, this::completed);
        runTasks();

        assertEquals("0:1", stored());
        assertEquals(1, completions);
    }

    @Test
    public void shouldLeaveLastMemberKeepingEpoch()
    {
        seed("5:1");
        final MqttKafkaRegistry registry = newRegistry(replace("5:1", "5:0"));

        registry.leave(KEY, this::completed);
        runTasks();

        assertEquals("5:0", stored());
        assertEquals(1, completions);
    }

    @Test
    public void shouldNotLeaveBelowZero()
    {
        seed("5:0");
        final MqttKafkaRegistry registry = newRegistry();

        registry.leave(KEY, this::completed);
        runTasks();

        assertEquals("5:0", stored());
        assertEquals(1, completions);
    }

    @Test
    public void shouldNotLeaveAbsentEntry()
    {
        final MqttKafkaRegistry registry = newRegistry();

        registry.leave(KEY, this::completed);
        runTasks();

        assertNull(stored());
        assertEquals(1, completions);
    }

    @Test
    public void shouldNotLeaveUnreadableEntry()
    {
        seed("x");
        final MqttKafkaRegistry registry = newRegistry();

        registry.leave(KEY, this::completed);
        runTasks();

        assertEquals("x", stored());
        assertEquals(1, completions);
    }

    @Test
    public void shouldRetryLeaveWhenReplaceLosesRace()
    {
        seed("0:2");
        final MqttKafkaRegistry registry = newRegistry(replace("0:2", "0:1"), replace("0:3", "0:2"));

        registry.leave(KEY, this::completed);
        seed("0:3");
        runTasks();

        assertEquals("0:2", stored());
        assertEquals(1, completions);
    }

    @Test
    public void shouldGiveUpLeaveWhenAttemptsExhausted()
    {
        seed("0:2");
        final List<Assertion> expected = new ArrayList<>();
        for (int attempt = 0; attempt < MqttKafkaRegistry.ATTEMPTS_MAX; attempt++)
        {
            expected.add(replace("0:" + (attempt + 2), "0:" + (attempt + 1)));
        }
        final MqttKafkaRegistry registry = newRegistry(expected.toArray(Assertion[]::new));

        registry.leave(KEY, this::completed);
        for (int attempt = 0; attempt < MqttKafkaRegistry.ATTEMPTS_MAX; attempt++)
        {
            seed("0:" + (attempt + 3));
            runLostAttempt();
        }
        runTasks();

        assertEquals(1, completions);
        assertEquals(0, failures);
        assertEquals("0:" + (MqttKafkaRegistry.ATTEMPTS_MAX + 2), stored());
    }

    private MqttKafkaRegistry newRegistry(
        Assertion... assertions)
    {
        final Queue<Assertion> expected = new ConcurrentLinkedQueue<>(List.of(assertions));

        return new MqttKafkaRegistry(
            new TestStoreHandler(null, tasks::add, entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), expected));
    }

    private void failed()
    {
        failures++;
    }

    private void completed()
    {
        completions++;
    }

    private void seed(
        String value)
    {
        entries.put(KEY, new TestEntry(value, Long.MAX_VALUE));
    }

    private String stored()
    {
        final TestEntry entry = entries.get(KEY);
        return entry != null ? entry.value() : null;
    }

    private void runLostAttempt()
    {
        for (int round = 0; round < 2 && !tasks.isEmpty(); round++)
        {
            tasks.remove(0).run();
        }
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

    private static Assertion putIfAbsent(
        String value)
    {
        return new Assertion("putIfAbsent", KEY, value, null);
    }

    private static Assertion replace(
        String expected,
        String value)
    {
        return new Assertion("replace", KEY, null, value, expected, 0L, null);
    }
}
