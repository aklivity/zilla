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
package io.aklivity.zilla.runtime.classifier.semantic.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class SemanticReferencesTest
{
    private static final List<String> PHRASES = List.of("alpha", "beta");
    private static final float[][] VECTORS = {{1.0f, 0.0f}, {0.0f, 1.0f}};
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Map<Long, IntConsumer> scheduled = new LinkedHashMap<>();
    private final Map<Long, Integer> scheduledIds = new LinkedHashMap<>();

    private EmbeddingHandler embedding;
    private StoreHandler store;
    private Signaler signaler;
    private SemanticReferences references;
    private long nextCancelId;

    @Before
    public void init()
    {
        embedding = mock(EmbeddingHandler.class);
        store = mock(StoreHandler.class);
        signaler = mock(Signaler.class);
        nextCancelId = 1L;

        when(signaler.signalAt(anyLong(), anyInt(), any(IntConsumer.class))).thenAnswer(invocation ->
        {
            long cancelId = nextCancelId++;
            scheduledIds.put(cancelId, invocation.getArgument(1));
            scheduled.put(cancelId, invocation.getArgument(2));
            return cancelId;
        });
        when(signaler.cancel(anyLong())).thenAnswer(invocation ->
        {
            long cancelId = invocation.getArgument(0);
            scheduledIds.remove(cancelId);
            return scheduled.remove(cancelId) != null;
        });

        references = new SemanticReferences(embedding, store, signaler, PHRASES, LOCK_TTL, TIMEOUT);
    }

    @Test
    public void shouldBecomeReadyFromCachedVectors()
    {
        RecordingWaiter waiter = new RecordingWaiter();

        references.start();
        references.whenReady(waiter);
        assertThat(waiter.ready, nullValue());

        completeGet(SemanticVectorCodec.encode(VECTORS));

        assertThat(waiter.ready, equalTo(VECTORS));
        verify(store, never()).lock(anyString(), any(), any());
        verify(embedding, never()).embed(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    public void shouldCallWaiterImmediatelyWhenAlreadyReady()
    {
        references.start();
        completeGet(SemanticVectorCodec.encode(VECTORS));
        RecordingWaiter waiter = new RecordingWaiter();

        references.whenReady(waiter);

        assertThat(waiter.ready, equalTo(VECTORS));
    }

    @Test
    public void shouldEmbedCacheAndUnlockWhenLockAcquired()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);

        completeGet(null);
        completeLock("token0");
        completeEmbed(VECTORS);

        assertThat(waiter.ready, equalTo(VECTORS));

        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Consumer<String>> put = captor(Consumer.class);
        verify(store).put(eq(references.cacheKey()), encoded.capture(), eq(null), put.capture());
        assertThat(SemanticVectorCodec.decode(encoded.getValue(), 2), equalTo(VECTORS));

        put.getValue().accept(null);
        verify(store).unlock(eq(references.lockKey()), eq("token0"), any());
    }

    @Test
    public void shouldEmbedWhenCachedVectorsAreMalformed()
    {
        references.start();

        completeGet("1.0,abc;-");

        verify(store).lock(eq(references.lockKey()), eq(LOCK_TTL), any());
    }

    @Test
    public void shouldPollCacheWhenLockHeldElsewhere()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);

        completeGet(null);
        completeLock(null);
        verify(embedding, never()).embed(anyLong(), anyLong(), anyLong(), any(), any());

        fire(SemanticReferences.RETRY_SIGNAL_ID);
        verify(store, times(2)).get(eq(references.cacheKey()), any());
        completeGet(SemanticVectorCodec.encode(VECTORS));

        assertThat(waiter.ready, equalTo(VECTORS));
    }

    @Test
    public void shouldBackOffBetweenCachePolls()
    {
        references.start();
        completeGet(null);
        completeLock(null);

        fire(SemanticReferences.RETRY_SIGNAL_ID);
        completeGet(null);
        completeLock(null);
        List<Long> times = scheduledTimes();

        assertThat(times.get(1) - times.get(0) >= SemanticReferences.INITIAL_RETRY_DELAY_MILLIS, equalTo(true));
    }

    @Test
    public void shouldRemainNotReadyAndRetryWhenEmbeddingFails()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        completeGet(null);
        completeLock("token0");

        failEmbed(new IllegalStateException("embedding unavailable"));

        assertThat(waiter.ready, nullValue());
        assertThat(waiter.failed, nullValue());
        verify(store, never()).put(anyString(), anyString(), any(), any());
        verify(store).unlock(eq(references.lockKey()), eq("token0"), any());

        fire(SemanticReferences.RETRY_SIGNAL_ID);
        completeGet(null);
        completeLock("token1");
        completeEmbed(VECTORS);

        assertThat(waiter.ready, equalTo(VECTORS));
    }

    @Test
    public void shouldTreatNullVectorAsFailure()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        completeGet(null);
        completeLock("token0");

        completeEmbed(new float[][] {{1.0f, 0.0f}, null});

        assertThat(waiter.ready, nullValue());
        verify(store, never()).put(anyString(), anyString(), any(), any());
        verify(store).unlock(eq(references.lockKey()), eq("token0"), any());
    }

    @Test
    public void shouldTreatWrongVectorCountAsFailure()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        completeGet(null);
        completeLock("token0");

        completeEmbed(new float[][] {{1.0f, 0.0f}});

        assertThat(waiter.ready, nullValue());
        verify(store, never()).put(anyString(), anyString(), any(), any());
    }

    @Test
    public void shouldTreatNullResultsAsFailure()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        completeGet(null);
        completeLock("token0");

        completeEmbed(null);

        assertThat(waiter.ready, nullValue());
        verify(store, never()).put(anyString(), anyString(), any(), any());
    }

    @Test
    public void shouldRenewLockWhileEmbedding()
    {
        references.start();
        completeGet(null);
        completeLock("token0");

        fire(SemanticReferences.RENEW_SIGNAL_ID);
        verify(store).renew(eq(references.lockKey()), eq("token0"), eq(LOCK_TTL), any());
        completeRenew("token0");

        fire(SemanticReferences.RENEW_SIGNAL_ID);
        verify(store, times(2)).renew(eq(references.lockKey()), eq("token0"), eq(LOCK_TTL), any());
    }

    @Test
    public void shouldStopRenewingAndSkipUnlockWhenLeaseLost()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        completeGet(null);
        completeLock("token0");
        fire(SemanticReferences.RENEW_SIGNAL_ID);

        completeRenew(null);
        completeEmbed(VECTORS);

        assertThat(scheduledIds.containsValue(SemanticReferences.RENEW_SIGNAL_ID), equalTo(false));
        assertThat(waiter.ready, equalTo(VECTORS));
        verify(store).put(eq(references.cacheKey()), anyString(), eq(null), any());
        verify(store, never()).unlock(anyString(), anyString(), any());
    }

    @Test
    public void shouldCancelRenewalWhenEmbeddingCompletes()
    {
        references.start();
        completeGet(null);
        completeLock("token0");
        assertThat(scheduledIds.containsValue(SemanticReferences.RENEW_SIGNAL_ID), equalTo(true));

        completeEmbed(VECTORS);

        assertThat(scheduledIds.containsValue(SemanticReferences.RENEW_SIGNAL_ID), equalTo(false));
    }

    @Test
    public void shouldFailWaiterWhenNotReadyInTime()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);

        fire(SemanticReferences.TIMEOUT_SIGNAL_ID);

        assertThat(waiter.failed, instanceOf(TimeoutException.class));

        completeGet(SemanticVectorCodec.encode(VECTORS));
        assertThat(waiter.ready, nullValue());
    }

    @Test
    public void shouldCancelWaiterTimeoutWhenReady()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        assertThat(scheduledIds.containsValue(SemanticReferences.TIMEOUT_SIGNAL_ID), equalTo(true));

        completeGet(SemanticVectorCodec.encode(VECTORS));

        assertThat(scheduledIds.containsValue(SemanticReferences.TIMEOUT_SIGNAL_ID), equalTo(false));
    }

    @Test
    public void shouldFailPendingWaitersWhenClosed()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);

        references.close();

        assertThat(waiter.failed, instanceOf(IllegalStateException.class));
        assertThat(scheduled.isEmpty(), equalTo(true));
    }

    @Test
    public void shouldFailLaterWhenWaitingAfterClose()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.close();

        references.whenReady(waiter);
        assertThat(waiter.failed, nullValue());

        fire(SemanticReferences.TIMEOUT_SIGNAL_ID);
        assertThat(waiter.failed, instanceOf(IllegalStateException.class));
    }

    @Test
    public void shouldUnlockWhenClosedWhileEmbedding()
    {
        references.start();
        completeGet(null);
        completeLock("token0");

        references.close();

        verify(store).unlock(eq(references.lockKey()), eq("token0"), any());
        assertThat(scheduled.isEmpty(), equalTo(true));
    }

    @Test
    public void shouldIgnoreLateResponsesWhenClosed()
    {
        RecordingWaiter waiter = new RecordingWaiter();
        references.start();
        references.whenReady(waiter);
        completeGet(null);
        completeLock("token0");
        references.close();

        completeEmbed(VECTORS);

        assertThat(waiter.ready, nullValue());
        verify(store, never()).put(anyString(), anyString(), any(), any());
    }

    @Test
    public void shouldNotRetryAfterClose()
    {
        references.start();
        completeGet(null);
        completeLock(null);

        references.close();

        assertThat(scheduled.isEmpty(), equalTo(true));
    }

    private List<Long> scheduledTimes()
    {
        ArgumentCaptor<Long> times = ArgumentCaptor.forClass(Long.class);
        verify(signaler, atLeastOnce()).signalAt(times.capture(), eq(SemanticReferences.RETRY_SIGNAL_ID), any(IntConsumer.class));
        return times.getAllValues();
    }

    private void fire(
        int signalId)
    {
        Long cancelId = scheduledIds.entrySet().stream()
            .filter(entry -> entry.getValue() == signalId)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);

        if (cancelId != null)
        {
            scheduledIds.remove(cancelId);
            scheduled.remove(cancelId).accept(signalId);
        }
        else
        {
            throw new AssertionError("signal not scheduled: " + signalId);
        }
    }

    private void completeGet(
        String value)
    {
        ArgumentCaptor<BiConsumer<String, String>> get = captor(BiConsumer.class);
        verify(store, atLeastOnce()).get(eq(references.cacheKey()), get.capture());
        get.getValue().accept(references.cacheKey(), value);
    }

    private void completeLock(
        String token)
    {
        ArgumentCaptor<BiConsumer<String, String>> lock = captor(BiConsumer.class);
        verify(store, atLeastOnce()).lock(eq(references.lockKey()), eq(LOCK_TTL), lock.capture());
        lock.getValue().accept(references.lockKey(), token);
    }

    private void completeRenew(
        String token)
    {
        ArgumentCaptor<Consumer<String>> renew = captor(Consumer.class);
        verify(store, atLeastOnce()).renew(eq(references.lockKey()), anyString(), eq(LOCK_TTL), renew.capture());
        renew.getValue().accept(token);
    }

    private void completeEmbed(
        float[][] results)
    {
        embedCallback().completed(0L, results);
    }

    private void failEmbed(
        Throwable ex)
    {
        embedCallback().failed(0L, ex);
    }

    private EmbeddingHandler.CompletionCallback embedCallback()
    {
        ArgumentCaptor<EmbeddingHandler.CompletionCallback> callback =
            ArgumentCaptor.forClass(EmbeddingHandler.CompletionCallback.class);
        verify(embedding, atLeastOnce()).embed(anyLong(), anyLong(), anyLong(), eq(PHRASES), callback.capture());
        return callback.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<T> captor(
        Class<?> type)
    {
        return (ArgumentCaptor) ArgumentCaptor.forClass(type);
    }

    private static final class RecordingWaiter implements SemanticReferences.Waiter
    {
        private float[][] ready;
        private Throwable failed;

        @Override
        public void ready(
            float[][] vectors)
        {
            this.ready = vectors;
        }

        @Override
        public void failed(
            Throwable ex)
        {
            this.failed = ex;
        }
    }
}
