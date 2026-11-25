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

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.store.config.TestStoreOptionsConfig.Assertion;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestEntry;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

public class MqttSessionExpirerTest
{
    private static final String SHARE_NAME = "consumers";
    private static final String FILTER = "sensor/one";
    private static final String COUNT_KEY = MqttSharedRegistry.key(SHARE_NAME, FILTER);
    private static final String STATE_KEY = "one#state";
    private static final String MEMBER_KEY = MqttSharedRegistry.memberKey(SHARE_NAME, FILTER, "one");
    private static final byte[] NO_SUBSCRIPTIONS = new byte[0];
    private static final byte[] NO_OUTBOUND = new byte[0];
    private static final byte[] NO_INBOUND = new byte[0];

    private final ConcurrentMap<String, TestEntry> entries = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Long> scheduledTimes = new ArrayList<>();
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final MqttClientStates states = new MqttClientStates();

    private long now = 1_000L;
    private final List<Closeable> attached = new ArrayList<>();

    @After
    public void close() throws IOException
    {
        for (Closeable closeable : attached)
        {
            closeable.close();
        }
    }

    @Test
    public void shouldReleaseMembershipOfStateExpiredBeforeAttach()
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        seedExpired(STATE_KEY, state("lifetime-1", null));

        attach(newStore(null));
        runTasks();

        assertEquals("0:1", count());
        assertEquals(MqttSharedRegistry.RELEASED, member());
    }

    @Test
    public void shouldReleaseMembershipOfStateExpiringAfterAttach()
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        final TestStoreHandler store = newStore(null);
        attach(store);

        store.put(STATE_KEY, state("lifetime-1", null), Duration.ofSeconds(1), stored -> {});
        advance(1_001L);

        assertEquals("0:1", count());
        assertEquals(MqttSharedRegistry.RELEASED, member());
    }

    @Test
    public void shouldReleaseMembershipOfHeldState()
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        seedExpired(STATE_KEY, state("lifetime-1", "replica-1-7"));

        attach(newStore(null));
        runTasks();

        assertEquals("0:1", count());
        assertEquals(MqttSharedRegistry.RELEASED, member());
    }

    @Test
    public void shouldNotReleaseMembershipOfHeldStateWhileOwnerRecordPresent()
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        seedOwner();
        seedExpired(STATE_KEY, state("lifetime-1", "replica-1-7"));

        attach(newStore(null));
        runTasks();

        assertEquals("0:2", count());
        assertEquals("lifetime-1", member());
    }

    @Test
    public void shouldNotReleaseMembershipOfStateWhileOwnerRecordPresent()
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        seedOwner();
        seedExpired(STATE_KEY, state("lifetime-1", null));

        attach(newStore(null));
        runTasks();

        assertEquals("0:2", count());
        assertEquals("lifetime-1", member());
    }

    @Test
    public void shouldReleaseEachMembershipOfExpiredState()
    {
        final String otherCountKey = MqttSharedRegistry.key(SHARE_NAME, "sensor/two");
        final String otherMemberKey = MqttSharedRegistry.memberKey(SHARE_NAME, "sensor/two", "one");
        seedCount("0:2");
        seedMember("lifetime-1");
        entries.put(otherCountKey, new TestEntry("3:5", Long.MAX_VALUE));
        entries.put(otherMemberKey, new TestEntry("lifetime-2", Long.MAX_VALUE));
        seedExpired(STATE_KEY, states.encode(List.of(
            new MqttMembershipRecord(SHARE_NAME, FILTER, "lifetime-1"),
            new MqttMembershipRecord(SHARE_NAME, "sensor/two", "lifetime-2")), List.of(), null,
            NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND));

        attach(newStore(null));
        runTasks();

        assertEquals("0:1", count());
        assertEquals("3:4", entries.get(otherCountKey).value());
        assertEquals(MqttSharedRegistry.RELEASED, entries.get(otherMemberKey).value());
    }

    @Test
    public void shouldNotReleaseMembershipTwiceWhenTwoRegistrantsObserveExpiry()
    {
        seedCount("0:3");
        seedMember("lifetime-1");
        final TestStoreHandler store = newStore(null);
        attach(store);
        attach(store);

        store.put(STATE_KEY, state("lifetime-1", null), Duration.ofSeconds(1), stored -> {});
        advance(1_001L);

        assertEquals("0:2", count());
        assertEquals(MqttSharedRegistry.RELEASED, member());
    }

    @Test
    public void shouldNotReleaseMembershipOfNewerSession()
    {
        seedCount("0:1");
        seedMember("lifetime-2");
        seedExpired(STATE_KEY, state("lifetime-1", null));

        attach(newStore(null));
        runTasks();

        assertEquals("0:1", count());
        assertEquals("lifetime-2", member());
    }

    @Test
    public void shouldLeaveMembershipRecordedWithoutToken()
    {
        seedCount("0:2");
        seedExpired(STATE_KEY, states.encode(
            List.of(new MqttMembershipRecord(SHARE_NAME, FILTER)), List.of(), null, NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND));

        attach(newStore(null));
        runTasks();

        assertEquals("0:1", count());
        assertNull(member());
    }

    @Test
    public void shouldReleaseMembershipOfClientIdEndingInStateKey()
    {
        final String memberKey = MqttSharedRegistry.memberKey(SHARE_NAME, FILTER, "a#state");
        seedCount("0:2");
        entries.put(memberKey, new TestEntry("lifetime-1", Long.MAX_VALUE));
        seedExpired("a#state#state", state("lifetime-1", null));

        attach(newStore(null));
        runTasks();

        assertEquals("0:1", count());
        assertEquals(MqttSharedRegistry.RELEASED, entries.get(memberKey).value());
    }

    @Test
    public void shouldIgnoreExpiryOfKeysOutsideStateFamily()
    {
        seedExpired("one#owner", "replica-1:7");
        seedExpired(COUNT_KEY, "0:1");
        seedExpired(MEMBER_KEY, "lifetime-1");
        seedExpired(MqttSharedRegistry.memberKey(SHARE_NAME, FILTER, "x#state"), "lifetime-1");
        seedExpired("one#state#owner", "replica-1:7");
        seedExpired("unrelated", state("lifetime-1", null));

        attach(newStore(new ConcurrentLinkedQueue<>()));
        runTasks();

        assertNull(count());
        assertNull(member());
    }

    @Test
    public void shouldIgnoreExpiredStateThatCannotBeDecoded()
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        seedExpired(STATE_KEY, "CQ");

        attach(newStore(new ConcurrentLinkedQueue<>()));
        runTasks();

        assertEquals("0:2", count());
        assertEquals("lifetime-1", member());
    }

    @Test
    public void shouldIgnoreExpiredStateWithoutMemberships()
    {
        seedExpired(STATE_KEY, states.encode(List.of(), List.of(), null, NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND));

        attach(newStore(new ConcurrentLinkedQueue<>()));
        runTasks();

        assertNull(count());
    }

    @Test
    public void shouldStopReleasingWhenDetached() throws IOException
    {
        seedCount("0:2");
        seedMember("lifetime-1");
        final TestStoreHandler store = newStore(null);
        attach(store);
        attached.get(0).close();

        store.put(STATE_KEY, state("lifetime-1", null), Duration.ofSeconds(1), stored -> {});
        advance(1_001L);

        assertEquals("0:2", count());
        assertEquals("lifetime-1", member());
    }

    private void attach(
        TestStoreHandler store)
    {
        final MqttSessionExpirer expirer = new MqttSessionExpirer(store, new MqttSharedRegistry(store));
        attached.add(expirer.attach());
    }

    private TestStoreHandler newStore(
        Queue<Assertion> assertions)
    {
        return new TestStoreHandler(null, tasks::add, () -> now, (time, task) ->
        {
            scheduledTimes.add(time);
            scheduledTasks.add(task);
        }, entries, new ConcurrentHashMap<>(), new CopyOnWriteArrayList<>(), new ConcurrentHashMap<>(), assertions);
    }

    private String state(
        String token,
        String holder)
    {
        return states.encode(
            List.of(new MqttMembershipRecord(SHARE_NAME, FILTER, token)), List.of(), holder,
            NO_SUBSCRIPTIONS, NO_OUTBOUND, NO_INBOUND);
    }

    private void seedCount(
        String value)
    {
        entries.put(COUNT_KEY, new TestEntry(value, Long.MAX_VALUE));
    }

    private void seedMember(
        String token)
    {
        entries.put(MEMBER_KEY, new TestEntry(token, Long.MAX_VALUE));
    }

    private void seedOwner()
    {
        entries.put("one#owner", new TestEntry("replica-1-7|token", Long.MAX_VALUE));
    }

    private void seedExpired(
        String key,
        String value)
    {
        entries.put(key, new TestEntry(value, now - 1L));
    }

    private String count()
    {
        final TestEntry entry = entries.get(COUNT_KEY);
        return entry != null ? entry.value() : null;
    }

    private String member()
    {
        final TestEntry entry = entries.get(MEMBER_KEY);
        return entry != null ? entry.value() : null;
    }

    private void advance(
        long millis)
    {
        now += millis;

        for (int index = 0; index < scheduledTimes.size(); index++)
        {
            if (scheduledTimes.get(index) <= now)
            {
                scheduledTasks.get(index).run();
            }
        }

        runTasks();
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
}
