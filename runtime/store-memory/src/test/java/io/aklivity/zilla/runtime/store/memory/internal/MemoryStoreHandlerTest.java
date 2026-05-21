/*
 * Copyright 2021-2024 Aklivity Inc
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.Closeable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntConsumer;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.LockEntry;
import io.aklivity.zilla.runtime.store.memory.internal.MemoryStoreHandler.Watcher;

public class MemoryStoreHandlerTest
{
    private ConcurrentMap<String, MemoryEntry> entries;
    private ConcurrentMap<String, List<Watcher>> watchers;
    private ConcurrentMap<String, LockEntry> locks;

    @Before
    public void setUp()
    {
        entries = new ConcurrentHashMap<>();
        watchers = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
    }

    @Test
    public void shouldFireListenerAfterPut()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final List<String> values = new ArrayList<>();
        handler.watch("k", (key, value) -> values.add(value));

        handler.put("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);

        signaler.runOne();
        assertThat(values, contains("v1"));
    }

    @Test
    public void shouldFireListenerWithNullOnDelete()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        handler.put("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        final List<String> values = new ArrayList<>();
        handler.watch("k", (key, value) -> values.add(value));

        handler.delete("k", MemoryStoreHandlerTest::ignored);
        signaler.runOne();

        assertThat(values, contains((String) null));
    }

    @Test
    public void shouldFireListenerOnGetAndDelete()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        handler.put("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        final List<String> values = new ArrayList<>();
        handler.watch("k", (key, value) -> values.add(value));

        handler.getAndDelete("k", MemoryStoreHandlerTest::ignored);
        signaler.runOne();

        assertThat(values, contains((String) null));
    }

    @Test
    public void shouldFireListenerOnPutIfAbsentWhenStored()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final List<String> values = new ArrayList<>();
        handler.watch("k", (key, value) -> values.add(value));

        handler.putIfAbsent("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.runOne();

        assertThat(values, contains("v1"));
    }

    @Test
    public void shouldNotFireListenerOnPutIfAbsentWhenExisting()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        handler.put("k", "existing", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        final List<String> values = new ArrayList<>();
        handler.watch("k", (key, value) -> values.add(value));

        handler.putIfAbsent("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        assertThat(values, is(empty()));
    }

    @Test
    public void shouldNotFireListenerOnDeleteWhenAbsent()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final List<String> values = new ArrayList<>();
        handler.watch("k", (key, value) -> values.add(value));

        handler.delete("k", MemoryStoreHandlerTest::ignored);
        signaler.drain();

        assertThat(values, is(empty()));
    }

    @Test
    public void shouldUnsubscribeOnClose() throws Exception
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final List<String> values = new ArrayList<>();
        final Closeable subscription = handler.watch("k", (key, value) -> values.add(value));

        subscription.close();

        handler.put("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        assertThat(values, is(empty()));
    }

    @Test
    public void shouldDispatchToRegisteringWorkerWhenPutFromAnother()
    {
        final RecordingSignaler signalerA = new RecordingSignaler();
        final RecordingSignaler signalerB = new RecordingSignaler();

        final MemoryStoreHandler handlerA = new MemoryStoreHandler(entries, watchers, locks, signalerA);
        final MemoryStoreHandler handlerB = new MemoryStoreHandler(entries, watchers, locks, signalerB);

        final List<String> received = new ArrayList<>();
        handlerA.watch("k", (key, value) -> received.add(value));

        // mutate on handler B; listener must dispatch via handler A's signaler
        handlerB.put("k", "v-from-B", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);

        // draining only B's signaler runs the put completion but NOT the listener
        signalerB.drain();
        assertThat(received, is(empty()));

        // listener fires only when A's signaler is drained
        signalerA.drain();
        assertThat(received, contains("v-from-B"));
    }

    @Test
    public void shouldFanOutToMultipleWatchers()
    {
        final RecordingSignaler signalerA = new RecordingSignaler();
        final RecordingSignaler signalerB = new RecordingSignaler();

        final MemoryStoreHandler handlerA = new MemoryStoreHandler(entries, watchers, locks, signalerA);
        final MemoryStoreHandler handlerB = new MemoryStoreHandler(entries, watchers, locks, signalerB);

        final List<String> receivedA = new ArrayList<>();
        final List<String> receivedB = new ArrayList<>();
        handlerA.watch("k", (key, value) -> receivedA.add(value));
        handlerB.watch("k", (key, value) -> receivedB.add(value));

        handlerA.put("k", "v1", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);

        signalerA.drain();
        signalerB.drain();

        assertThat(receivedA, contains("v1"));
        assertThat(receivedB, contains("v1"));
    }

    @Test
    public void shouldNotFireListenerForOtherKey()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final List<String> values = new ArrayList<>();
        handler.watch("k1", (key, value) -> values.add(value));

        handler.put("k2", "v", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        assertThat(values, is(empty()));
    }

    @Test
    public void shouldReturnCloseableFromWatch()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final Closeable subscription = handler.watch("k", MemoryStoreHandlerTest::ignored);
        assertNotNull(subscription);
    }

    @Test
    public void shouldAcquireLockWhenAbsent()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final String[] tokenRef = new String[1];
        handler.lock("k", Long.MAX_VALUE, (key, token) -> tokenRef[0] = token);
        signaler.drain();

        assertThat(tokenRef[0], is(notNullValue()));
    }

    @Test
    public void shouldNotAcquireLockWhenHeld()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        handler.lock("k", Long.MAX_VALUE, MemoryStoreHandlerTest::ignored);
        signaler.drain();

        final String[] tokenRef = new String[1];
        tokenRef[0] = "sentinel";
        handler.lock("k", Long.MAX_VALUE, (key, token) -> tokenRef[0] = token);
        signaler.drain();

        assertNull(tokenRef[0]);
    }

    @Test
    public void shouldReleaseLockOnUnlockWithMatchingToken()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final String[] heldToken = new String[1];
        handler.lock("k", Long.MAX_VALUE, (key, token) -> heldToken[0] = token);
        signaler.drain();

        final String[] unlockResult = new String[]{ "sentinel" };
        handler.unlock("k", heldToken[0], result -> unlockResult[0] = result);
        signaler.drain();
        assertNull(unlockResult[0]);

        final String[] reacquired = new String[1];
        handler.lock("k", Long.MAX_VALUE, (key, token) -> reacquired[0] = token);
        signaler.drain();
        assertThat(reacquired[0], is(notNullValue()));
    }

    @Test
    public void shouldRejectUnlockWithMismatchedToken()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final String[] heldToken = new String[1];
        handler.lock("k", Long.MAX_VALUE, (key, token) -> heldToken[0] = token);
        signaler.drain();

        final String[] unlockResult = new String[1];
        handler.unlock("k", "wrong-token", result -> unlockResult[0] = result);
        signaler.drain();
        assertThat(unlockResult[0], is(equalTo(heldToken[0])));

        // lock still held — re-attempt fails
        final String[] reattempt = new String[]{ "sentinel" };
        handler.lock("k", Long.MAX_VALUE, (key, token) -> reattempt[0] = token);
        signaler.drain();
        assertNull(reattempt[0]);
    }

    @Test
    public void shouldAcquireLockAfterTtlExpiry() throws Exception
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final String[] firstToken = new String[1];
        handler.lock("k", 10L, (key, token) -> firstToken[0] = token);
        signaler.drain();
        assertNotNull(firstToken[0]);

        Thread.sleep(25L);

        final String[] secondToken = new String[1];
        handler.lock("k", Long.MAX_VALUE, (key, token) -> secondToken[0] = token);
        signaler.drain();
        assertThat(secondToken[0], is(notNullValue()));
        assertThat(secondToken[0], is(not(equalTo(firstToken[0]))));
    }

    @Test
    public void shouldTreatUnlockOfExpiredAsNoOp() throws Exception
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final String[] heldToken = new String[1];
        handler.lock("k", 10L, (key, token) -> heldToken[0] = token);
        signaler.drain();

        Thread.sleep(25L);

        final String[] unlockResult = new String[]{ "sentinel" };
        handler.unlock("k", heldToken[0], result -> unlockResult[0] = result);
        signaler.drain();
        assertNull(unlockResult[0]);
    }

    @Test
    public void shouldTreatUnlockOfMissingAsNoOp()
    {
        final RecordingSignaler signaler = new RecordingSignaler();
        final MemoryStoreHandler handler = new MemoryStoreHandler(entries, watchers, locks, signaler);

        final String[] unlockResult = new String[]{ "sentinel" };
        handler.unlock("k", "any-token", result -> unlockResult[0] = result);
        signaler.drain();
        assertNull(unlockResult[0]);
    }

    @Test
    public void shouldArbitrateLockAcrossWorkers()
    {
        final RecordingSignaler signalerA = new RecordingSignaler();
        final RecordingSignaler signalerB = new RecordingSignaler();

        final MemoryStoreHandler handlerA = new MemoryStoreHandler(entries, watchers, locks, signalerA);
        final MemoryStoreHandler handlerB = new MemoryStoreHandler(entries, watchers, locks, signalerB);

        final String[] tokenA = new String[1];
        handlerA.lock("k", Long.MAX_VALUE, (key, token) -> tokenA[0] = token);
        signalerA.drain();
        assertNotNull(tokenA[0]);

        final String[] tokenB = new String[]{ "sentinel" };
        handlerB.lock("k", Long.MAX_VALUE, (key, token) -> tokenB[0] = token);
        signalerB.drain();
        assertNull(tokenB[0]);

        handlerA.unlock("k", tokenA[0], MemoryStoreHandlerTest::ignored);
        signalerA.drain();

        final String[] tokenB2 = new String[1];
        handlerB.lock("k", Long.MAX_VALUE, (key, token) -> tokenB2[0] = token);
        signalerB.drain();
        assertThat(tokenB2[0], is(notNullValue()));
    }

    private static void ignored(
        String unused)
    {
    }

    private static void ignored(
        String unusedKey,
        String unusedValue)
    {
    }

    /**
     * Captures scheduled callbacks so the test can drive them deterministically on its own thread.
     */
    private static final class RecordingSignaler implements Signaler
    {
        private final List<IntConsumer> scheduled = new ArrayList<>();

        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            scheduled.add(handler);
            return NO_CANCEL_ID;
        }

        @Override
        public long signalAt(
            Instant time,
            int signalId,
            IntConsumer handler)
        {
            return signalAt(time.toEpochMilli(), signalId, handler);
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
        }

        @Override
        public long signalAt(
            long timeMillis,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            return NO_CANCEL_ID;
        }

        @Override
        public long signalAt(
            Instant time,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            return NO_CANCEL_ID;
        }

        @Override
        public long signalTask(
            Runnable task,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            return NO_CANCEL_ID;
        }

        @Override
        public boolean cancel(
            long cancelId)
        {
            return false;
        }

        IntConsumer pollScheduled()
        {
            return scheduled.isEmpty() ? null : scheduled.remove(0);
        }

        void runOne()
        {
            final IntConsumer handler = pollScheduled();
            if (handler != null)
            {
                handler.accept(0);
            }
        }

        void drain()
        {
            while (!scheduled.isEmpty())
            {
                scheduled.remove(0).accept(0);
            }
        }
    }
}
