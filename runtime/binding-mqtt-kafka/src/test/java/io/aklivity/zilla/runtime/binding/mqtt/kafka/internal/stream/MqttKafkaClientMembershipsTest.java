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
import io.aklivity.zilla.runtime.engine.test.internal.store.TestEntry;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

public class MqttKafkaClientMembershipsTest
{
    private static final String KEY = "zilla:test:memberships:client";
    private static final MqttKafkaMembership ONE = new MqttKafkaMembership("consumers", "sensor/one");
    private static final MqttKafkaMembership TWO = new MqttKafkaMembership("consumers", "sensor/two");
    private static final MqttKafkaMembership THREE = new MqttKafkaMembership("others", "sensor/one");

    private final MqttKafkaMemberships codec = new MqttKafkaMemberships();
    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> epochs = new ArrayList<>();
    private final List<Boolean> durable = new ArrayList<>();
    private int failures;
    private int settled;

    @Test
    public void shouldJoinFirstSubscriber()
    {
        final MqttKafkaClientMemberships memberships = newMemberships(
            putIfAbsent(ONE, "0:1"),
            put(ONE));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        assertFalse(memberships.idle());
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals(List.of(true), durable);
        assertEquals("0:1", registry(ONE));
        assertEquals(codec.encode(List.of(ONE)), stored(KEY));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldJoinLiveSubscription()
    {
        seedRegistry(ONE, "0:1");
        final MqttKafkaClientMemberships memberships = newMemberships(
            replace(ONE, "0:1", "0:2"),
            put(ONE));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertEquals("0:2", registry(ONE));
    }

    @Test
    public void shouldBeginNewLifeWhenJoiningEmptySubscription()
    {
        seedRegistry(ONE, "0:0");
        final MqttKafkaClientMemberships memberships = newMemberships(
            replace(ONE, "0:0", "1:1"),
            put(ONE));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(1L), epochs);
        assertEquals("1:1", registry(ONE));
    }

    @Test
    public void shouldJoinListedSubscriptionWithoutChangingRegistry()
    {
        seed(KEY, codec.encode(List.of(ONE)));
        seedRegistry(ONE, "2:3");
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(2L), epochs);
        assertEquals("2:3", registry(ONE));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldJoinListedSubscriptionOfAbsentRegistryAtFirstEpoch()
    {
        seed(KEY, codec.encode(List.of(ONE)));
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertNull(registry(ONE));
    }

    @Test
    public void shouldJoinDistinctSubscriptionsInOrder()
    {
        final MqttKafkaClientMemberships memberships = newMemberships(
            putIfAbsent(ONE, "0:1"),
            put(ONE),
            putIfAbsent(TWO, "0:1"),
            put(ONE, TWO));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        memberships.join("consumers", "sensor/two", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(0L, 0L), epochs);
        assertEquals(codec.encode(List.of(ONE, TWO)), stored(KEY));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldJoinSameSubscriptionOnce()
    {
        final MqttKafkaClientMemberships memberships = newMemberships(
            putIfAbsent(ONE, "0:1"),
            put(ONE));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(0L, 0L), epochs);
        assertEquals("0:1", registry(ONE));
    }

    @Test
    public void shouldFailJoinWhenMembershipsUnreadable()
    {
        seed(KEY, "AgQAAAAAAAAA");
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertTrue(epochs.isEmpty());
        assertEquals(1, failures);
        assertNull(registry(ONE));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldFailJoinWhenRegistryUnreadable()
    {
        seedRegistry(ONE, "x");
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertTrue(epochs.isEmpty());
        assertEquals(1, failures);
        assertNull(stored(KEY));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldReconcileRemovedMembershipAfterWritingMemberships()
    {
        seed(KEY, codec.encode(List.of(ONE, TWO)));
        seedRegistry(ONE, "0:1");
        seedRegistry(TWO, "0:2");
        final MqttKafkaClientMemberships memberships = newMemberships(
            put(ONE),
            replace(TWO, "0:2", "0:1"));

        memberships.reconcile(List.of(ONE));
        runTasks();

        assertEquals(codec.encode(List.of(ONE)), stored(KEY));
        assertEquals("0:1", registry(ONE));
        assertEquals("0:1", registry(TWO));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldReconcileAllMembershipsWhenNoneSubscribed()
    {
        seed(KEY, codec.encode(List.of(ONE, TWO)));
        seedRegistry(ONE, "0:1");
        seedRegistry(TWO, "3:2");
        final MqttKafkaClientMemberships memberships = newMemberships(
            delete(),
            replace(ONE, "0:1", "0:0"),
            replace(TWO, "3:2", "3:1"));

        memberships.reconcile(List.of());
        runTasks();

        assertNull(stored(KEY));
        assertEquals("0:0", registry(ONE));
        assertEquals("3:1", registry(TWO));
    }

    @Test
    public void shouldNotReconcileWhenAllMembershipsSubscribed()
    {
        seed(KEY, codec.encode(List.of(ONE, TWO)));
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.reconcile(List.of(TWO, ONE, THREE));
        runTasks();

        assertEquals(codec.encode(List.of(ONE, TWO)), stored(KEY));
        assertTrue(memberships.idle());
        assertEquals(1, settled);
    }

    @Test
    public void shouldNotReconcileUnreadableMemberships()
    {
        seed(KEY, "AgQAAAAAAAAA");
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.reconcile(List.of());
        runTasks();

        assertEquals("AgQAAAAAAAAA", stored(KEY));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldDiscardMembershipsAfterDeletingEntry()
    {
        seed(KEY, codec.encode(List.of(ONE, TWO)));
        seedRegistry(ONE, "0:2");
        seedRegistry(TWO, "1:1");
        final MqttKafkaClientMemberships memberships = newMemberships(
            delete(),
            replace(ONE, "0:2", "0:1"),
            replace(TWO, "1:1", "1:0"));

        memberships.discard();
        assertFalse(memberships.idle());
        runTasks();

        assertNull(stored(KEY));
        assertEquals("0:1", registry(ONE));
        assertEquals("1:0", registry(TWO));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldDiscardUnreadableMembershipsEntry()
    {
        seed(KEY, "AgQAAAAAAAAA");
        final MqttKafkaClientMemberships memberships = newMemberships(delete());

        memberships.discard();
        runTasks();

        assertNull(stored(KEY));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldNotDiscardWhenNoMemberships()
    {
        final MqttKafkaClientMemberships memberships = newMemberships();

        memberships.discard();
        runTasks();

        assertTrue(memberships.idle());
        assertEquals(1, settled);
    }

    @Test
    public void shouldDiscardMembershipJoinedWhileJoining()
    {
        final MqttKafkaClientMemberships memberships = newMemberships(
            putIfAbsent(ONE, "0:1"),
            put(ONE),
            delete(),
            replace(ONE, "0:1", "0:0"));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        memberships.discard();
        runTasks();

        assertEquals(List.of(0L), epochs);
        assertNull(stored(KEY));
        assertEquals("0:0", registry(ONE));
        assertTrue(memberships.idle());
    }

    @Test
    public void shouldBeginNewLifeWhenJoiningAfterDiscard()
    {
        seed(KEY, codec.encode(List.of(ONE)));
        seedRegistry(ONE, "0:1");
        final MqttKafkaClientMemberships memberships = newMemberships(
            delete(),
            replace(ONE, "0:1", "0:0"),
            replace(ONE, "0:0", "1:1"),
            put(ONE));

        memberships.discard();
        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        runTasks();

        assertEquals(List.of(1L), epochs);
        assertEquals("1:1", registry(ONE));
        assertEquals(codec.encode(List.of(ONE)), stored(KEY));
    }

    @Test
    public void shouldReconcileMembershipJoinedWhileJoining()
    {
        final MqttKafkaClientMemberships memberships = newMemberships(
            putIfAbsent(ONE, "0:1"),
            put(ONE),
            delete(),
            replace(ONE, "0:1", "0:0"));

        memberships.join("consumers", "sensor/one", this::joined, this::failed);
        memberships.reconcile(List.of());
        runTasks();

        assertNull(stored(KEY));
        assertEquals("0:0", registry(ONE));
    }

    private MqttKafkaClientMemberships newMemberships(
        Assertion... assertions)
    {
        final Queue<Assertion> expected = new ConcurrentLinkedQueue<>(List.of(assertions));
        final TestStoreHandler store =
            new TestStoreHandler(null, tasks::add, entries, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), expected);

        return new MqttKafkaClientMemberships(store, KEY, codec, new MqttKafkaRegistry(store),
            MqttKafkaClientMembershipsTest::registryKey, () -> settled++);
    }

    private void joined(
        long epoch)
    {
        epochs.add(epoch);
        durable.add(stored(KEY) != null);
    }

    private void failed()
    {
        failures++;
    }

    private void seed(
        String key,
        String value)
    {
        entries.put(key, new TestEntry(value, Long.MAX_VALUE));
    }

    private void seedRegistry(
        MqttKafkaMembership membership,
        String value)
    {
        seed(registryKey(membership.shareName, membership.filter), value);
    }

    private String registry(
        MqttKafkaMembership membership)
    {
        return stored(registryKey(membership.shareName, membership.filter));
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

    private static String registryKey(
        String shareName,
        String filter)
    {
        return String.format("zilla:test:share:%s:%s:registry", shareName, filter);
    }

    private Assertion putIfAbsent(
        MqttKafkaMembership membership,
        String value)
    {
        return new Assertion("putIfAbsent", registryKey(membership.shareName, membership.filter), value, null);
    }

    private Assertion replace(
        MqttKafkaMembership membership,
        String expected,
        String value)
    {
        return new Assertion("replace", registryKey(membership.shareName, membership.filter), null, value, expected, 0L, null);
    }

    private Assertion put(
        MqttKafkaMembership... listed)
    {
        return new Assertion("put", KEY, codec.encode(List.of(listed)), null);
    }

    private static Assertion delete()
    {
        return new Assertion("delete", KEY, null, null);
    }
}
