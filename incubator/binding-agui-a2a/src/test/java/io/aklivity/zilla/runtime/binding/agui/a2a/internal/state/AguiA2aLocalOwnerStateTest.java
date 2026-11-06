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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aHandleKind.INTERRUPT;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aHandleKind.TOOL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
import java.util.Random;
import java.util.function.IntConsumer;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

public class AguiA2aLocalOwnerStateTest
{
    private static final long THREAD_TTL = 60_000L;
    private static final long HANDLE_TTL = 10_000L;

    private final AguiA2aThreadKey key = new AguiA2aThreadKey("alice", "weather", "thread-1");

    private final ManualSignaler signaler = new ManualSignaler();
    private long now;
    private AguiA2aLocalOwnerState state;

    @Before
    public void init()
    {
        now = 1_000_000L;
        state = new AguiA2aLocalOwnerState(signaler, () -> now, new Random(1L), THREAD_TTL, HANDLE_TTL, 2);
    }

    @Test
    public void shouldRememberContext()
    {
        state.rememberContext(key, "ctx-1");

        assertThat(state.lookupContext(key), equalTo("ctx-1"));
    }

    @Test
    public void shouldStoreNothingWhenAgentReturnsNoContext()
    {
        state.rememberContext(key, null);

        assertThat(state.lookupContext(key), nullValue());
        assertThat(signaler.handler, nullValue());
    }

    @Test
    public void shouldIsolateKeys()
    {
        state.rememberContext(key, "ctx-1");

        assertThat(state.lookupContext(new AguiA2aThreadKey("bob", "weather", "thread-1")), nullValue());
        assertThat(state.lookupContext(new AguiA2aThreadKey(null, "weather", "thread-1")), nullValue());
        assertThat(state.lookupContext(new AguiA2aThreadKey("alice", "other", "thread-1")), nullValue());
        assertThat(state.lookupContext(new AguiA2aThreadKey("alice", "weather", "thread-2")), nullValue());
    }

    @Test
    public void shouldExpireContextWhenIdle()
    {
        state.rememberContext(key, "ctx-1");
        now += THREAD_TTL;

        assertThat(state.lookupContext(key), nullValue());
    }

    @Test
    public void shouldSlideContextExpiryOnLookup()
    {
        state.rememberContext(key, "ctx-1");
        now += THREAD_TTL - 1L;
        assertThat(state.lookupContext(key), equalTo("ctx-1"));
        now += THREAD_TTL - 1L;

        assertThat(state.lookupContext(key), equalTo("ctx-1"));
    }

    @Test
    public void shouldEvictLeastRecentlyUsedContext()
    {
        final AguiA2aThreadKey key1 = new AguiA2aThreadKey("alice", "weather", "thread-2");
        final AguiA2aThreadKey key2 = new AguiA2aThreadKey("alice", "weather", "thread-3");

        state.rememberContext(key, "ctx-1");
        state.rememberContext(key1, "ctx-2");
        state.lookupContext(key);
        state.rememberContext(key2, "ctx-3");

        assertThat(state.lookupContext(key), equalTo("ctx-1"));
        assertThat(state.lookupContext(key1), nullValue());
        assertThat(state.lookupContext(key2), equalTo("ctx-3"));
    }

    @Test
    public void shouldIssueUnguessableHandle()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "task-1");
        final String other = state.issueHandle(key, INTERRUPT, "task-1");

        assertThat(handle, hasLength(32));
        assertThat(handle, not(equalTo(other)));
    }

    @Test
    public void shouldResolveHandleOnce()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "task-1");

        assertThat(state.resolveHandle(key, INTERRUPT, handle), equalTo("task-1"));
        assertThat(state.resolveHandle(key, INTERRUPT, handle), nullValue());
    }

    @Test
    public void shouldRejectUnknownHandle()
    {
        assertThat(state.resolveHandle(key, INTERRUPT, "0123456789abcdef0123456789abcdef"), nullValue());
        assertThat(state.resolveHandle(key, INTERRUPT, null), nullValue());
    }

    @Test
    public void shouldRejectExpiredHandle()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "task-1");
        now += HANDLE_TTL;

        assertThat(state.resolveHandle(key, INTERRUPT, handle), nullValue());
    }

    @Test
    public void shouldRejectForeignHandleAndKeepItForOwner()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "task-1");

        assertThat(state.resolveHandle(new AguiA2aThreadKey("bob", "weather", "thread-1"), INTERRUPT, handle), nullValue());
        assertThat(state.resolveHandle(new AguiA2aThreadKey("alice", "other", "thread-1"), INTERRUPT, handle), nullValue());
        assertThat(state.resolveHandle(new AguiA2aThreadKey("alice", "weather", "thread-2"), INTERRUPT, handle), nullValue());
        assertThat(state.resolveHandle(key, INTERRUPT, handle), equalTo("task-1"));
    }

    @Test
    public void shouldRejectHandleOfDifferentKind()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "task-1");

        assertThat(state.resolveHandle(key, TOOL, handle), nullValue());
        assertThat(state.resolveHandle(key, INTERRUPT, handle), equalTo("task-1"));
    }

    @Test
    public void shouldEvictOldestHandleAtCapacity()
    {
        final String handle0 = state.issueHandle(key, INTERRUPT, "task-0");
        final String handle1 = state.issueHandle(key, INTERRUPT, "task-1");
        final String handle2 = state.issueHandle(key, INTERRUPT, "task-2");

        assertThat(state.resolveHandle(key, INTERRUPT, handle0), nullValue());
        assertThat(state.resolveHandle(key, INTERRUPT, handle1), equalTo("task-1"));
        assertThat(state.resolveHandle(key, INTERRUPT, handle2), equalTo("task-2"));
    }

    @Test
    public void shouldSweepExpiredEntriesThroughSignaler()
    {
        state.rememberContext(key, "ctx-1");
        state.issueHandle(key, INTERRUPT, "task-1");

        assertThat(signaler.timeMillis, equalTo(now + HANDLE_TTL));

        now += THREAD_TTL;
        signaler.fire();

        assertThat(signaler.handler, nullValue());
        assertThat(state.lookupContext(key), nullValue());
    }

    @Test
    public void shouldRearmSweepWhileEntriesRemain()
    {
        state.rememberContext(key, "ctx-1");

        now += HANDLE_TTL;
        signaler.fire();

        assertThat(signaler.timeMillis, equalTo(now + HANDLE_TTL));
        assertThat(state.lookupContext(key), equalTo("ctx-1"));
    }

    private static final class ManualSignaler implements Signaler
    {
        private long timeMillis;
        private IntConsumer handler;

        void fire()
        {
            final IntConsumer fired = handler;
            handler = null;
            fired.accept(1);
        }

        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            this.timeMillis = timeMillis;
            this.handler = handler;
            return 1L;
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
            throw new UnsupportedOperationException();
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId,
            DirectBufferEx buffer,
            int offset,
            int length)
        {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(
            long cancelId)
        {
            throw new UnsupportedOperationException();
        }
    }
}
