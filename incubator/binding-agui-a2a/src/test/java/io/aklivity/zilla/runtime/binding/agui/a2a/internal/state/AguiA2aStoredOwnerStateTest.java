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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class AguiA2aStoredOwnerStateTest
{
    private static final Duration THREAD_TTL = Duration.ofMinutes(1);
    private static final Duration HANDLE_TTL = Duration.ofSeconds(10);

    private final AguiA2aThreadKey key = new AguiA2aThreadKey("alice", "weather", "thread-1");
    private final AguiA2aThreadKey other = new AguiA2aThreadKey("bob", "weather", "thread-1");
    private final String contextKey = "agui-a2a/ctx/" + String.format("%016x", key.hash());

    private final AtomicLong handleCount = new AtomicLong();
    private final ManualSignaler signaler = new ManualSignaler();
    private final FakeStore store = new FakeStore();
    private long now;
    private AguiA2aStoredOwnerState state;

    @Before
    public void init()
    {
        now = 1_000_000L;
        state = newState();
    }

    @Test
    public void shouldWriteThroughContext()
    {
        state.rememberContext(key, "ctx-1");

        assertThat(store.entries.get(contextKey), equalTo("{\"contextId\":\"ctx-1\"}"));
        assertThat(store.ttls.get(contextKey), equalTo(THREAD_TTL));
        assertThat(state.lookupContext(key), equalTo("ctx-1"));
    }

    @Test
    public void shouldWriteThroughHandle()
    {
        final String handle = state.issueHandle(key, TOOL, "t-1", "call-1");

        final String handleKey = "agui-a2a/h/" + handle;
        assertThat(store.entries.get(handleKey), equalTo(
            "{\"keyHash\":\"" + String.format("%016x", key.hash()) + "\",\"kind\":\"TOOL\"," +
            "\"taskId\":\"t-1\",\"subject\":\"call-1\"}"));
        assertThat(store.ttls.get(handleKey), equalTo(HANDLE_TTL));
    }

    @Test
    public void shouldWriteThroughHandleWithoutSubject()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "t-1");

        assertThat(store.entries.get("agui-a2a/h/" + handle), equalTo(
            "{\"keyHash\":\"" + String.format("%016x", key.hash()) + "\",\"kind\":\"INTERRUPT\",\"taskId\":\"t-1\"}"));
    }

    @Test
    public void shouldRestoreContextInlineOnLocalHit()
    {
        state.rememberContext(key, "ctx-1");
        store.reads = 0;

        final AtomicBoolean ready = new AtomicBoolean();
        state.restoreContext(key, () -> ready.set(true));

        assertThat(ready.get(), is(true));
        assertThat(store.reads, equalTo(0));
    }

    @Test
    public void shouldRestoreContextOnLocalMiss()
    {
        store.entries.put(contextKey, "{\"contextId\":\"ctx-7\"}");

        final AtomicBoolean ready = new AtomicBoolean();
        state.restoreContext(key, () -> ready.set(true));

        assertThat(ready.get(), is(false));
        assertThat(state.lookupContext(key), nullValue());

        store.drain();

        assertThat(ready.get(), is(true));
        assertThat(state.lookupContext(key), equalTo("ctx-7"));
    }

    @Test
    public void shouldCompleteRestoreContextWhenAbsent()
    {
        final AtomicBoolean ready = new AtomicBoolean();
        state.restoreContext(key, () -> ready.set(true));
        store.drain();

        assertThat(ready.get(), is(true));
        assertThat(state.lookupContext(key), nullValue());
    }

    @Test
    public void shouldRestoreContextAfterRestart()
    {
        state.rememberContext(key, "ctx-1");
        store.drain();

        final AguiA2aStoredOwnerState restarted = newState();
        restarted.restoreContext(key, () -> {});
        store.drain();

        assertThat(restarted.lookupContext(key), equalTo("ctx-1"));
    }

    @Test
    public void shouldConsumeHandleInStoreWhenResolvedLocally()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "t-1");
        store.drain();

        assertThat(state.resolveHandle(key, INTERRUPT, handle), equalTo("t-1"));
        store.drain();

        assertThat(store.entries.containsKey("agui-a2a/h/" + handle), is(false));
    }

    @Test
    public void shouldRestoreHandleOnLocalMiss()
    {
        final String handle = state.issueHandle(key, TOOL, "t-1", "call-1");
        store.drain();

        final AguiA2aStoredOwnerState restarted = newState();
        final AtomicBoolean ready = new AtomicBoolean();
        restarted.restoreHandle(key, TOOL, handle, () -> ready.set(true));

        assertThat(ready.get(), is(false));

        store.drain();

        assertThat(ready.get(), is(true));
        assertThat(store.entries.containsKey("agui-a2a/h/" + handle), is(false));

        final AguiA2aHandleTarget target = restarted.resolveTarget(key, TOOL, handle);
        assertThat(target, not(nullValue()));
        assertThat(target.taskId(), equalTo("t-1"));
        assertThat(target.subject(), equalTo("call-1"));
        assertThat(restarted.resolveTarget(key, TOOL, handle), nullValue());
    }

    @Test
    public void shouldRestoreHandleInlineOnLocalHit()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "t-1");
        store.reads = 0;

        final AtomicBoolean ready = new AtomicBoolean();
        state.restoreHandle(key, INTERRUPT, handle, () -> ready.set(true));

        assertThat(ready.get(), is(true));
        assertThat(store.reads, equalTo(0));
    }

    @Test
    public void shouldCompleteRestoreHandleWhenUnknown()
    {
        final AtomicBoolean ready = new AtomicBoolean();
        state.restoreHandle(key, INTERRUPT, "missing", () -> ready.set(true));
        store.drain();

        assertThat(ready.get(), is(true));
        assertThat(state.resolveHandle(key, INTERRUPT, "missing"), nullValue());
    }

    @Test
    public void shouldCompleteRestoreHandleWhenNull()
    {
        final AtomicBoolean ready = new AtomicBoolean();
        state.restoreHandle(key, INTERRUPT, null, () -> ready.set(true));

        assertThat(ready.get(), is(true));
        assertThat(store.reads, equalTo(0));
    }

    @Test
    public void shouldTreatKeyHashMismatchAsMiss()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "t-1");
        store.drain();

        final AguiA2aStoredOwnerState restarted = newState();
        restarted.restoreHandle(other, INTERRUPT, handle, () -> {});
        store.drain();

        assertThat(restarted.resolveHandle(other, INTERRUPT, handle), nullValue());
        assertThat(store.entries.containsKey("agui-a2a/h/" + handle), is(true));

        restarted.restoreHandle(key, INTERRUPT, handle, () -> {});
        store.drain();

        assertThat(restarted.resolveHandle(key, INTERRUPT, handle), equalTo("t-1"));
    }

    @Test
    public void shouldTreatKindMismatchAsMiss()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "t-1");
        store.drain();

        final AguiA2aStoredOwnerState restarted = newState();
        restarted.restoreHandle(key, TOOL, handle, () -> {});
        store.drain();

        assertThat(restarted.resolveHandle(key, TOOL, handle), nullValue());
        assertThat(store.entries.containsKey("agui-a2a/h/" + handle), is(true));
    }

    @Test
    public void shouldNotResurrectConsumedHandle()
    {
        final String handle = state.issueHandle(key, INTERRUPT, "t-1");
        store.drain();
        state.resolveHandle(key, INTERRUPT, handle);
        store.drain();

        final AguiA2aStoredOwnerState restarted = newState();
        restarted.restoreHandle(key, INTERRUPT, handle, () -> {});
        store.drain();

        assertThat(restarted.resolveHandle(key, INTERRUPT, handle), nullValue());
    }

    private AguiA2aStoredOwnerState newState()
    {
        final AguiA2aLocalOwnerState local = new AguiA2aLocalOwnerState(signaler, () -> now,
            () -> String.format("%032x", handleCount.incrementAndGet()), THREAD_TTL.toMillis(), HANDLE_TTL.toMillis(), 16);

        return new AguiA2aStoredOwnerState(local, store, THREAD_TTL, HANDLE_TTL);
    }

    private static final class FakeStore implements StoreHandler
    {
        private final Map<String, String> entries = new HashMap<>();
        private final Map<String, Duration> ttls = new HashMap<>();
        private final Queue<Runnable> completions = new ArrayDeque<>();

        private int reads;

        void drain()
        {
            while (!completions.isEmpty())
            {
                completions.remove().run();
            }
        }

        @Override
        public void get(
            String key,
            BiConsumer<String, String> completion)
        {
            reads++;
            final String value = entries.get(key);
            completions.add(() -> completion.accept(key, value));
        }

        @Override
        public void put(
            String key,
            String value,
            Duration ttl,
            Consumer<String> completion)
        {
            entries.put(key, value);
            ttls.put(key, ttl);
            completions.add(() -> completion.accept(null));
        }

        @Override
        public void putIfAbsent(
            String key,
            String value,
            Duration ttl,
            Consumer<String> completion)
        {
            final String existing = entries.putIfAbsent(key, value);
            ttls.putIfAbsent(key, ttl);
            completions.add(() -> completion.accept(existing));
        }

        @Override
        public void delete(
            String key,
            Consumer<String> completion)
        {
            entries.remove(key);
            ttls.remove(key);
            completions.add(() -> completion.accept(null));
        }

        @Override
        public void getAndDelete(
            String key,
            Consumer<String> completion)
        {
            reads++;
            final String value = entries.remove(key);
            ttls.remove(key);
            completions.add(() -> completion.accept(value));
        }

        @Override
        public void lock(
            String key,
            Duration ttl,
            BiConsumer<String, String> completion)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unlock(
            String key,
            String token,
            Consumer<String> completion)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void renew(
            String key,
            String token,
            Duration ttl,
            Consumer<String> completion)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Closeable watch(
            String key,
            BiConsumer<String, String> listener)
        {
            throw new UnsupportedOperationException();
        }
    }
}
