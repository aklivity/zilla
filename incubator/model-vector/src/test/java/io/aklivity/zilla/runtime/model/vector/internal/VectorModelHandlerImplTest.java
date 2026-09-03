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
package io.aklivity.zilla.runtime.model.vector.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.model.vector.VectorModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;
import io.aklivity.zilla.runtime.engine.test.internal.embedding.TestEmbeddingHandler;
import io.aklivity.zilla.runtime.engine.test.internal.store.TestStoreHandler;

// Simulates two per-worker VectorModelHandlerImpl instances sharing one store (as store-memory
// shares its backing map across every EngineWorker in one process) to prove only one of them ever
// calls EmbeddingHandler.embed() for the same reject phrases, and both still resolve correctly.
// Each simulated worker gets its own task queue (its embedding handler and store handler both
// defer completions onto it) so the test can drain one worker at a time, deterministically, while
// the two share only the underlying store maps -- exactly the part being deduplicated.
@SuppressWarnings({ "rawtypes", "unchecked" })
public class VectorModelHandlerImplTest
{
    private static final int FLAGS_FIN = 0x01;
    private static final Runnable NOOP = () ->
    {
    };

    // Raw: TestStoreHandler's own lock-entry/watcher value types are package-private, so these
    // fields can only be typed generically enough to still be passed to its constructor unchanged.
    private final ConcurrentMap<String, String> entries = new ConcurrentHashMap<>();
    private final ConcurrentMap listeners = new ConcurrentHashMap();
    private final ConcurrentMap locks = new ConcurrentHashMap();
    private final FakeSignaler signaler = new FakeSignaler();
    private final AtomicInteger embedCalls = new AtomicInteger();

    private VectorModelConfig config;

    @Before
    public void init()
    {
        config = VectorModelConfig.builder()
            .embedding("moderator0")
            .reject("reject this message")
            .threshold(0.99)
            .store("cache0")
            .build();
    }

    @Test
    public void shouldEmbedOnceAcrossTwoWorkersSharingAStore()
    {
        // GIVEN -- first worker attaches: cache miss, wins the lock, starts (but hasn't finished) a
        // real, counted embed call
        Queue<Runnable> tasks1 = new ArrayDeque<>();
        VectorModelHandlerImpl first = new VectorModelHandlerImpl(newWorkerContext(tasks1), config);
        drainOne(tasks1);
        drainOne(tasks1);
        assertThat(embedCalls.get(), equalTo(1));

        // WHEN -- second worker attaches before the first worker's embed call has completed:
        // cache still empty, loses the lock race, and schedules a retry instead of embedding
        Queue<Runnable> tasks2 = new ArrayDeque<>();
        VectorModelHandlerImpl second = new VectorModelHandlerImpl(newWorkerContext(tasks2), config);
        drainOne(tasks2);
        drainOne(tasks2);

        // THEN -- second lost the lock race and scheduled a retry instead of embedding
        assertThat(embedCalls.get(), equalTo(1));
        assertThat(signaler.pending(), equalTo(1));

        // WHEN -- the winner's embed call completes and caches the result
        drainOne(tasks1);

        // WHEN -- the loser's scheduled retry fires and picks up the cached result
        signaler.fireNext();
        drain(tasks2);

        // THEN -- exactly one real reject-phrase embed call total, ever, across both workers
        // (isRejected below triggers its own, unrelated, per-message embed calls, so this is the
        // last point at which embedCalls only reflects reject-phrase warm-up work)
        assertThat(embedCalls.get(), equalTo(1));

        // THEN -- both workers resolve messages correctly regardless of which one actually embedded
        assertThat(isRejected(first, tasks1, "reject this message"), equalTo(true));
        assertThat(isRejected(first, tasks1, "an unrelated message"), equalTo(false));
        assertThat(isRejected(second, tasks2, "reject this message"), equalTo(true));
        assertThat(isRejected(second, tasks2, "an unrelated message"), equalTo(false));
    }

    @Test
    public void shouldNotCacheAFailureAndShouldReleaseTheLockForTheNextAttempt()
    {
        // GIVEN -- embed() itself fails for the one worker holding the lock
        Queue<Runnable> tasks1 = new ArrayDeque<>();
        EngineContext context = mock(EngineContext.class);
        EmbeddingHandler failing = new EmbeddingHandler()
        {
            @Override
            public void embed(
                long traceId,
                long bindingId,
                long contextId,
                List<String> texts,
                CompletionCallback completion)
            {
                embedCalls.incrementAndGet();
                tasks1.add(() -> completion.failed(contextId, new RuntimeException("boom")));
            }
        };
        when(context.supplyEmbedding(anyLong())).thenReturn(failing);
        when(context.supplyStore(anyLong())).thenReturn(newStoreHandler(tasks1));
        when(context.signaler()).thenReturn(signaler);

        VectorModelHandlerImpl handler = new VectorModelHandlerImpl(context, config);
        drain(tasks1);

        // THEN -- the failure was never cached, and the lock was released rather than left held
        assertThat(embedCalls.get(), equalTo(1));
        assertThat(entries.isEmpty(), equalTo(true));
        assertThat(locks.isEmpty(), equalTo(true));

        // WHEN -- a second worker attaches after the failure, using a real embedding backend
        Queue<Runnable> tasks2 = new ArrayDeque<>();
        VectorModelHandlerImpl retried = new VectorModelHandlerImpl(newWorkerContext(tasks2), config);
        drain(tasks2);

        // THEN -- exactly one more real reject-phrase embed call, by whichever worker asks next
        // (isRejected below triggers its own, unrelated, per-message embed calls, so this is the
        // last point at which embedCalls only reflects reject-phrase warm-up work)
        assertThat(embedCalls.get(), equalTo(2));

        // THEN -- the first worker's own local (failed) vectors never reject anything, but the
        // second worker's successful, cached result does
        assertThat(isRejected(handler, tasks1, "reject this message"), equalTo(false));
        assertThat(isRejected(retried, tasks2, "reject this message"), equalTo(true));
    }

    private EngineContext newWorkerContext(
        Queue<Runnable> tasks)
    {
        EngineContext context = mock(EngineContext.class);
        EmbeddingHandler delegate = new TestEmbeddingHandler(tasks::add);
        EmbeddingHandler counting = new EmbeddingHandler()
        {
            @Override
            public void embed(
                long traceId,
                long bindingId,
                long contextId,
                List<String> texts,
                CompletionCallback completion)
            {
                embedCalls.incrementAndGet();
                delegate.embed(traceId, bindingId, contextId, texts, completion);
            }
        };
        when(context.supplyEmbedding(anyLong())).thenReturn(counting);
        when(context.supplyStore(anyLong())).thenReturn(newStoreHandler(tasks));
        when(context.signaler()).thenReturn(signaler);
        return context;
    }

    private StoreHandler newStoreHandler(
        Queue<Runnable> tasks)
    {
        return new TestStoreHandler(null, tasks::add, entries, listeners, locks);
    }

    private static void drain(
        Queue<Runnable> tasks)
    {
        while (!tasks.isEmpty())
        {
            tasks.poll().run();
        }
    }

    private static void drainOne(
        Queue<Runnable> tasks)
    {
        tasks.poll().run();
    }

    private boolean isRejected(
        VectorModelHandlerImpl handler,
        Queue<Runnable> tasks,
        String text)
    {
        VectorModelPipeline pipeline = new VectorModelPipeline(handler, NOOP);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        UnsafeBufferEx src = new UnsafeBufferEx(bytes);
        UnsafeBufferEx dst = new UnsafeBufferEx(new byte[128]);

        pipeline.transform(0L, 0L, 0L, FLAGS_FIN, src, 0, src.capacity(), dst, 0, dst.capacity());
        drain(tasks);
        ModelPipelineResult result = pipeline.transform(0L, 0L, 0L, 0x00, src, 0, 0, dst, 0, dst.capacity());

        return result.status() == ModelStatus.REJECTED;
    }

    private static final class FakeSignaler implements Signaler
    {
        private final Queue<IntConsumer> scheduled = new ArrayDeque<>();

        int pending()
        {
            return scheduled.size();
        }

        void fireNext()
        {
            scheduled.poll().accept(0);
        }

        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            scheduled.add(handler);
            return 1L;
        }

        @Override
        public long signalAt(
            Instant time,
            int signalId,
            IntConsumer handler)
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
