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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketState.PUBLISH_SENT;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketState.PUBREL_PENDING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttSharedTicketState;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestEntry;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

public class MqttKafkaClientTicketsTest
{
    private static final String KEY = "zilla:test:tickets:client";

    private final MqttKafkaTickets codec = new MqttKafkaTickets();
    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Integer> loaded = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    @Test
    public void shouldLoadEmptyTicketsWhenAbsent()
    {
        final MqttKafkaClientTickets tickets = newTickets(newStore());

        assertFalse(tickets.ready());
        runTasks();

        assertTrue(tickets.ready());
        assertTrue(tickets.recover("consumers", "sensor/one").isEmpty());
    }

    @Test
    public void shouldReportLoadedTickets()
    {
        seed(KEY, codec.encode(List.of(
            newTicket(1, PUBLISH_SENT, "consumers", 5L),
            newTicket(2, PUBREL_PENDING, "others", 6L))));

        final MqttKafkaClientTickets tickets = newTickets(newStore());
        runTasks();

        assertEquals(List.of(1, 2), loaded);
        assertEquals(2, tickets.find(2).packetId);
        assertNull(tickets.find(3));
    }

    @Test
    public void shouldNotBeReadyWhenStoredTicketsUnreadable()
    {
        seed(KEY, "AgQAAAAAAAAA");

        final MqttKafkaClientTickets tickets = newTickets(newStore());
        runTasks();

        assertFalse(tickets.ready());
        assertTrue(tickets.recover("consumers", "sensor/one").isEmpty());
    }

    @Test
    public void shouldRecoverLoadedTicketsOfSubscriptionOnce()
    {
        seed(KEY, codec.encode(List.of(
            newTicket(1, PUBLISH_SENT, "consumers", 5L),
            newTicket(2, PUBREL_PENDING, "others", 6L),
            newTicket(3, PUBREL_PENDING, "consumers", 7L))));

        final MqttKafkaClientTickets tickets = newTickets(newStore());
        runTasks();

        final List<MqttKafkaTicket> recovered = tickets.recover("consumers", "sensor/one");

        assertEquals(2, recovered.size());
        assertEquals(1, recovered.get(0).packetId);
        assertEquals(3, recovered.get(1).packetId);
        assertTrue(tickets.recover("consumers", "sensor/one").isEmpty());
        assertEquals(1, tickets.recover("others", "sensor/one").size());
        assertTrue(tickets.recover("consumers", "sensor/two").isEmpty());
    }

    @Test
    public void shouldNotRecoverTicketsAddedAfterLoaded()
    {
        final MqttKafkaClientTickets tickets = newTickets(newStore());
        runTasks();

        tickets.add(newTicket(1, PUBLISH_SENT, "consumers", 5L));

        assertTrue(tickets.recover("consumers", "sensor/one").isEmpty());
    }

    @Test
    public void shouldNotRecoverTicketsRemovedAfterLoaded()
    {
        seed(KEY, codec.encode(List.of(newTicket(1, PUBLISH_SENT, "consumers", 5L))));

        final MqttKafkaClientTickets tickets = newTickets(newStore());
        runTasks();
        tickets.remove(1);

        assertTrue(tickets.recover("consumers", "sensor/one").isEmpty());
    }

    @Test
    public void shouldContinueAfterLoaded()
    {
        final MqttKafkaClientTickets tickets = newTickets(newStore());

        tickets.awaitLoaded(() -> events.add("first"));
        tickets.awaitLoaded(() -> events.add("second"));
        assertTrue(events.isEmpty());
        runTasks();
        tickets.awaitLoaded(() -> events.add("third"));

        assertEquals(List.of("first", "second", "third"), events);
    }

    @Test
    public void shouldWriteAddedTicket()
    {
        final MqttKafkaClientTickets tickets = newTickets(newStore(
            new Assertion("put", KEY, codec.encode(List.of(newTicket(1, PUBLISH_SENT, "consumers", 5L))), null)));
        runTasks();

        tickets.add(newTicket(1, PUBLISH_SENT, "consumers", 5L));
        tickets.write(() -> events.add("written"));
        assertFalse(tickets.idle());
        runTasks();

        assertTrue(tickets.idle());
        assertEquals(List.of("written"), events);
        assertEquals(codec.encode(List.of(newTicket(1, PUBLISH_SENT, "consumers", 5L))), stored(KEY));
    }

    @Test
    public void shouldCoalesceWritesWhileWriting()
    {
        final MqttKafkaClientTickets tickets = newTickets(newStore(
            new Assertion("put", KEY, codec.encode(List.of(newTicket(1, PUBLISH_SENT, "consumers", 5L))), null),
            new Assertion("put", KEY, codec.encode(List.of(
                newTicket(1, PUBLISH_SENT, "consumers", 5L),
                newTicket(2, PUBLISH_SENT, "consumers", 6L),
                newTicket(3, PUBLISH_SENT, "consumers", 7L))), null)));
        runTasks();

        tickets.add(newTicket(1, PUBLISH_SENT, "consumers", 5L));
        tickets.write(() -> events.add("first"));
        tickets.add(newTicket(2, PUBLISH_SENT, "consumers", 6L));
        tickets.write(() -> events.add("second"));
        tickets.add(newTicket(3, PUBLISH_SENT, "consumers", 7L));
        tickets.write(() -> events.add("third"));
        runTasks();

        assertEquals(List.of("first", "second", "third"), events);
        assertTrue(tickets.idle());
    }

    @Test
    public void shouldReplaceTicket()
    {
        final MqttKafkaTicket sent = newTicket(1, PUBLISH_SENT, "consumers", 5L);
        seed(KEY, codec.encode(List.of(sent)));
        final MqttKafkaClientTickets tickets = newTickets(newStore(
            new Assertion("put", KEY, codec.encode(List.of(sent.pubrelPending())), null)));
        runTasks();

        tickets.replace(sent.pubrelPending());
        tickets.write(() -> {});
        runTasks();

        assertEquals(PUBREL_PENDING, tickets.find(1).state);
    }

    @Test
    public void shouldDeleteEntryWhenLastTicketRemoved()
    {
        seed(KEY, codec.encode(List.of(newTicket(1, PUBLISH_SENT, "consumers", 5L))));
        final MqttKafkaClientTickets tickets = newTickets(newStore(new Assertion("delete", KEY, null, null)));
        runTasks();

        tickets.remove(1);
        tickets.write(() -> events.add("deleted"));
        runTasks();

        assertEquals(List.of("deleted"), events);
        assertNull(stored(KEY));
    }

    @Test
    public void shouldClearTicketsAndDeleteEntry()
    {
        seed(KEY, codec.encode(List.of(
            newTicket(1, PUBLISH_SENT, "consumers", 5L),
            newTicket(2, PUBREL_PENDING, "others", 6L))));
        final MqttKafkaClientTickets tickets = newTickets(newStore(new Assertion("delete", KEY, null, null)));
        runTasks();

        final List<MqttKafkaTicket> cleared = tickets.clear();
        tickets.write(() -> events.add("cleared"));
        runTasks();

        assertEquals(2, cleared.size());
        assertNull(tickets.find(1));
        assertEquals(List.of("cleared"), events);
        assertNull(stored(KEY));
    }

    @Test
    public void shouldClearUnreadableTicketsAndDeleteEntry()
    {
        seed(KEY, "AgQAAAAAAAAA");
        final MqttKafkaClientTickets tickets = newTickets(newStore(new Assertion("delete", KEY, null, null)));
        runTasks();

        final List<MqttKafkaTicket> cleared = tickets.clear();
        tickets.write(() -> {});
        runTasks();

        assertTrue(cleared.isEmpty());
        assertTrue(tickets.ready());
        assertNull(stored(KEY));
    }

    @Test
    public void shouldAbandonTicketAfterInFlightWriteCompletes()
    {
        final MqttKafkaTicket sent = newTicket(1, PUBLISH_SENT, "consumers", 5L);
        final MqttKafkaClientTickets tickets = newTickets(newStore(
            new Assertion("put", KEY, codec.encode(List.of(sent)), null),
            new Assertion("delete", KEY, null, null)));
        runTasks();

        tickets.add(sent);
        tickets.write(() -> events.add("durable"));
        tickets.abandon(1, () -> events.add("released"));

        assertEquals(List.of(), events);
        runTasks();

        assertEquals(List.of("durable", "released"), events);
        assertNull(tickets.find(1));
        assertNull(stored(KEY));
        assertTrue(tickets.idle());
    }

    @Test
    public void shouldAbandonTicketBeforePendingWriteIsIssued()
    {
        final MqttKafkaTicket first = newTicket(1, PUBLISH_SENT, "consumers", 5L);
        final MqttKafkaTicket second = newTicket(2, PUBLISH_SENT, "consumers", 6L);
        final MqttKafkaClientTickets tickets = newTickets(newStore(
            new Assertion("put", KEY, codec.encode(List.of(first)), null),
            new Assertion("put", KEY, codec.encode(List.of(first)), null)));
        runTasks();

        tickets.add(first);
        tickets.write(() -> events.add("first"));
        tickets.add(second);
        tickets.write(() -> events.add("second"));
        tickets.abandon(2, () -> events.add("released"));
        runTasks();

        assertEquals(List.of("first", "second", "released"), events);
        assertEquals(1, tickets.find(1).packetId);
        assertNull(tickets.find(2));
        assertEquals(codec.encode(List.of(first)), stored(KEY));
    }

    private MqttKafkaClientTickets newTickets(
        TestStoreHandler store)
    {
        return new MqttKafkaClientTickets(store, KEY, codec, t -> loaded.add(t.packetId), () -> {});
    }

    private TestStoreHandler newStore(
        Assertion... assertions)
    {
        final Queue<Assertion> expected = new ConcurrentLinkedQueue<>(List.of(assertions));

        return new TestStoreHandler(null, tasks::add, entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), expected);
    }

    private void seed(
        String key,
        String value)
    {
        entries.put(key, new TestEntry(value, Long.MAX_VALUE));
    }

    private String stored(
        String key)
    {
        final TestEntry entry = entries.get(key);
        return entry != null ? entry.value() : null;
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

    private static MqttKafkaTicket newTicket(
        int packetId,
        MqttSharedTicketState state,
        String shareName,
        long partitionOffset)
    {
        return new MqttKafkaTicket(packetId, state, shareName, "sensor/one", "mqtt-messages", 0, partitionOffset);
    }
}
