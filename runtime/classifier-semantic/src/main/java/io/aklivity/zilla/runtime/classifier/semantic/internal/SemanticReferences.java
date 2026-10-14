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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

// Every worker attaches the classifier independently, so without deduplication each worker (and, with a
// distributed store, each replica) would embed the same exemplar phrases at attach time. The store lets only
// the worker that wins a short-lived, renewed lock call the embedding; the others poll the cache with capped
// backoff until the winner's vectors appear, or take the lock themselves if the winner fails or loses its lease.
final class SemanticReferences
{
    static final int RETRY_SIGNAL_ID = 1;
    static final int RENEW_SIGNAL_ID = 2;
    static final int TIMEOUT_SIGNAL_ID = 3;
    static final long INITIAL_RETRY_DELAY_MILLIS = 100L;

    private static final String CACHE_KEY_PREFIX = "classifier.semantic.";
    private static final String LOCK_KEY_SUFFIX = ".lock";

    interface Waiter
    {
        void ready(
            float[][] vectors);

        void failed(
            Throwable ex);
    }

    private final EmbeddingHandler embedding;
    private final StoreHandler store;
    private final Signaler signaler;
    private final List<String> phrases;
    private final Duration lockTtl;
    private final Duration timeout;
    private final String cacheKey;
    private final String lockKey;
    private final List<Pending> pending;
    private final EmbeddingHandler.CompletionCallback embedded;

    private float[][] vectors;
    private String lockToken;
    private long retryDelayMillis;
    private long retryCancelId;
    private long renewCancelId;
    private boolean closed;

    SemanticReferences(
        EmbeddingHandler embedding,
        StoreHandler store,
        Signaler signaler,
        List<String> phrases,
        Duration lockTtl,
        Duration timeout)
    {
        this.embedding = embedding;
        this.store = store;
        this.signaler = signaler;
        this.phrases = phrases;
        this.lockTtl = lockTtl;
        this.timeout = timeout;
        this.cacheKey = CACHE_KEY_PREFIX + SemanticVectorCodec.digest(phrases);
        this.lockKey = cacheKey + LOCK_KEY_SUFFIX;
        this.pending = new LinkedList<>();
        this.embedded = new EmbeddingHandler.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                float[][] results)
            {
                onEmbedCompleted(results);
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                onEmbedFailed();
            }
        };
        this.retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
        this.retryCancelId = Signaler.NO_CANCEL_ID;
        this.renewCancelId = Signaler.NO_CANCEL_ID;
    }

    String cacheKey()
    {
        return cacheKey;
    }

    String lockKey()
    {
        return lockKey;
    }

    void start()
    {
        store.get(cacheKey, this::onCacheGet);
    }

    void whenReady(
        Waiter waiter)
    {
        if (closed)
        {
            signaler.signalAt(System.currentTimeMillis(), TIMEOUT_SIGNAL_ID,
                signalId -> waiter.failed(new IllegalStateException("closed")));
        }
        else if (vectors != null)
        {
            waiter.ready(vectors);
        }
        else
        {
            Pending entry = new Pending(waiter);
            entry.cancelId = signaler.signalAt(
                System.currentTimeMillis() + timeout.toMillis(), TIMEOUT_SIGNAL_ID, signalId -> onTimeout(entry));
            pending.add(entry);
        }
    }

    void close()
    {
        closed = true;

        cancel(retryCancelId);
        retryCancelId = Signaler.NO_CANCEL_ID;
        unlock();

        final List<Pending> drained = new ArrayList<>(pending);
        pending.clear();
        for (Pending entry : drained)
        {
            cancel(entry.cancelId);
            entry.waiter.failed(new IllegalStateException("closed"));
        }
    }

    private void onCacheGet(
        String key,
        String value)
    {
        if (!closed)
        {
            float[][] cached = SemanticVectorCodec.decode(value, phrases.size());
            if (cached != null)
            {
                settle(cached);
            }
            else
            {
                store.lock(lockKey, lockTtl, this::onLockAcquire);
            }
        }
    }

    private void onLockAcquire(
        String key,
        String token)
    {
        if (token == null)
        {
            if (!closed)
            {
                scheduleRetry();
            }
        }
        else
        {
            lockToken = token;

            if (closed)
            {
                unlock();
            }
            else
            {
                scheduleRenew();
                embedding.embed(0L, 0L, 0L, phrases, embedded);
            }
        }
    }

    private void onEmbedCompleted(
        float[][] results)
    {
        cancel(renewCancelId);
        renewCancelId = Signaler.NO_CANCEL_ID;

        if (closed)
        {
            unlock();
        }
        else if (SemanticVectorCodec.valid(results, phrases.size()))
        {
            store.put(cacheKey, SemanticVectorCodec.encode(results), null, ignored -> unlock());
            settle(results);
        }
        else
        {
            onEmbedFailed();
        }
    }

    private void onEmbedFailed()
    {
        unlock();

        if (!closed)
        {
            scheduleRetry();
        }
    }

    private void onRetry(
        int signalId)
    {
        retryCancelId = Signaler.NO_CANCEL_ID;

        if (!closed && vectors == null)
        {
            store.get(cacheKey, this::onCacheGet);
        }
    }

    private void onRenew(
        int signalId)
    {
        renewCancelId = Signaler.NO_CANCEL_ID;

        if (lockToken != null)
        {
            store.renew(lockKey, lockToken, lockTtl, this::onRenewComplete);
        }
    }

    private void onRenewComplete(
        String token)
    {
        if (token == null)
        {
            lockToken = null;
        }
        else if (lockToken != null && !closed)
        {
            scheduleRenew();
        }
    }

    private void onTimeout(
        Pending entry)
    {
        if (pending.remove(entry))
        {
            entry.waiter.failed(new TimeoutException("semantic classifier not ready within " + timeout));
        }
    }

    private void scheduleRetry()
    {
        retryCancelId = signaler.signalAt(System.currentTimeMillis() + retryDelayMillis, RETRY_SIGNAL_ID, this::onRetry);
        retryDelayMillis = Math.min(retryDelayMillis * 2L, lockTtl.toMillis());
    }

    private void scheduleRenew()
    {
        renewCancelId = signaler.signalAt(System.currentTimeMillis() + lockTtl.toMillis() / 2L, RENEW_SIGNAL_ID, this::onRenew);
    }

    private void unlock()
    {
        cancel(renewCancelId);
        renewCancelId = Signaler.NO_CANCEL_ID;

        if (lockToken != null)
        {
            store.unlock(lockKey, lockToken, this::onUnlockComplete);
            lockToken = null;
        }
    }

    // The store reports a failed unlock only when the lease already lapsed, which releases the lock on its own,
    // so there is nothing left to retry or repair.
    private void onUnlockComplete(
        String token)
    {
    }

    private void settle(
        float[][] settled)
    {
        vectors = settled;
        retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;
        cancel(retryCancelId);
        retryCancelId = Signaler.NO_CANCEL_ID;

        final List<Pending> drained = new ArrayList<>(pending);
        pending.clear();
        for (Pending entry : drained)
        {
            cancel(entry.cancelId);
            entry.waiter.ready(settled);
        }
    }

    private void cancel(
        long cancelId)
    {
        if (cancelId != Signaler.NO_CANCEL_ID)
        {
            signaler.cancel(cancelId);
        }
    }

    private static final class Pending
    {
        private final Waiter waiter;

        private long cancelId;

        private Pending(
            Waiter waiter)
        {
            this.waiter = waiter;
            this.cancelId = Signaler.NO_CANCEL_ID;
        }
    }
}
