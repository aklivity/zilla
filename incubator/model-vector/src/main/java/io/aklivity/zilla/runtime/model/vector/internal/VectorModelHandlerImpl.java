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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import io.aklivity.zilla.config.model.vector.VectorModelConfig;
import io.aklivity.zilla.runtime.common.vector.Vectors;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

// Per-worker factory for the vector model, resolving the named embedding once and embedding the
// configured reject phrases once, then vending a fresh per-stream VectorModelPipeline that reuses
// this handler's resolved embedding and reject vectors on every supplyDecoder/supplyEncoder call.
//
// Every namespace binding is replicated to and attached independently on every EngineWorker, so
// without deduplication every worker (and, with a distributed store, every replica) would embed
// the same reject phrases independently -- a redundant N-way burst against whatever embedding
// provider is configured, fired all at once at attach time. The required store instead lets only
// the worker that wins a short-lived lock do the real embed call; every other worker polls the
// cache with capped backoff until the winner's result appears, including after the lock owner
// fails without writing one (the lock simply expires and the next poll wins it instead).
final class VectorModelHandlerImpl implements ModelHandler
{
    private static final Runnable NOOP = () ->
    {
    };

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final long INITIAL_RETRY_DELAY_MILLIS = 100L;
    private static final int CACHE_RETRY_SIGNAL_ID = 1;
    private static final String NULL_VECTOR_TOKEN = "-";
    private static final String VECTOR_DELIMITER = ";";
    private static final String COMPONENT_DELIMITER = ",";

    private final EmbeddingHandler handler;
    private final List<String> reject;
    private final float[][] rejectVectors;
    private final double threshold;
    private final List<Runnable> pending;
    private final StoreHandler store;
    private final Signaler signaler;
    private final String cacheKey;
    private final String lockKey;

    private boolean ready;
    private int rejectVectorsReceived;
    private String lockToken;
    private long retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS;

    VectorModelHandlerImpl(
        EngineContext context,
        VectorModelConfig config)
    {
        this.handler = context.supplyEmbedding(config.embedding.id);
        this.reject = config.reject;
        this.threshold = config.threshold;
        this.pending = new LinkedList<>();
        this.rejectVectors = new float[config.reject.size()][];
        this.store = context.supplyStore(config.store.id);
        this.signaler = context.signaler();
        this.cacheKey = "model.vector.reject." + digest(config.reject);
        this.lockKey = cacheKey + ".lock";

        store.get(cacheKey, this::onCacheGet);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        ModelCache cache)
    {
        return supplyDecoder(envelope, transform, NOOP);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform)
    {
        return supplyEncoder(envelope, transform, NOOP);
    }

    @Override
    public ModelPipeline supplyDecoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return new VectorModelPipeline(this, resumed);
    }

    @Override
    public ModelPipeline supplyEncoder(
        ModelEnvelope envelope,
        ModelTransform transform,
        Runnable resumed)
    {
        return new VectorModelPipeline(this, resumed);
    }

    void embed(
        long traceId,
        long bindingId,
        String text,
        EmbeddingHandler.CompletionCallback callback)
    {
        handler.embed(traceId, bindingId, 0L, List.of(text), callback);
    }

    void whenReady(
        Runnable task)
    {
        if (ready)
        {
            task.run();
        }
        else
        {
            pending.add(task);
        }
    }

    boolean matches(
        float[] vector)
    {
        boolean matched = false;

        if (vector != null)
        {
            for (float[] rejectVector : rejectVectors)
            {
                if (rejectVector != null && Vectors.similarity(vector, rejectVector) >= threshold)
                {
                    matched = true;
                    break;
                }
            }
        }

        return matched;
    }

    private void embedRejectPhrases()
    {
        handler.embed(0L, 0L, 0L, reject, new EmbeddingHandler.CompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                float[][] results)
            {
                onEmbedComplete(results);
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                onEmbedFailed();
            }
        });
    }

    private void onEmbedComplete(
        float[][] results)
    {
        store.put(cacheKey, encode(results), null, ignored -> unlock());

        for (int i = 0; i < results.length; i++)
        {
            onRejectVectorEmbedded(i, results[i]);
        }
    }

    private void onEmbedFailed()
    {
        // Never cache a failure -- an unlucky transient error would otherwise permanently poison
        // every worker's (and, distributed, every replica's) result. Release the lock instead so
        // whichever worker polls next re-attempts the real embed call for itself.
        unlock();

        for (int i = 0; i < rejectVectors.length; i++)
        {
            onRejectVectorEmbedded(i, null);
        }
    }

    private void onCacheGet(
        String key,
        String value)
    {
        float[][] cached = value != null ? decode(value) : null;
        if (cached != null)
        {
            settle(cached);
        }
        else
        {
            store.lock(lockKey, LOCK_TTL, this::onLockAcquire);
        }
    }

    private void onLockAcquire(
        String key,
        String token)
    {
        if (token != null)
        {
            this.lockToken = token;
            embedRejectPhrases();
        }
        else
        {
            scheduleCacheRetry();
        }
    }

    private void scheduleCacheRetry()
    {
        signaler.signalAt(System.currentTimeMillis() + retryDelayMillis, CACHE_RETRY_SIGNAL_ID, this::onCacheRetry);
    }

    private void onCacheRetry(
        int signalId)
    {
        if (!ready)
        {
            store.get(cacheKey, this::onCacheRetryGet);
        }
    }

    private void onCacheRetryGet(
        String key,
        String value)
    {
        float[][] cached = value != null ? decode(value) : null;
        if (cached != null)
        {
            settle(cached);
        }
        else
        {
            retryDelayMillis = Math.min(retryDelayMillis * 2L, LOCK_TTL.toMillis());
            store.lock(lockKey, LOCK_TTL, this::onLockAcquire);
        }
    }

    private void unlock()
    {
        if (lockToken != null)
        {
            store.unlock(lockKey, lockToken, this::onUnlockComplete);
            lockToken = null;
        }
    }

    private void onUnlockComplete(
        String token)
    {
    }

    private void settle(
        float[][] vectors)
    {
        for (int i = 0; i < vectors.length; i++)
        {
            onRejectVectorEmbedded(i, vectors[i]);
        }
    }

    private void onRejectVectorEmbedded(
        int index,
        float[] vector)
    {
        rejectVectors[index] = vector;
        rejectVectorsReceived++;

        if (rejectVectorsReceived == rejectVectors.length)
        {
            ready = true;
            final List<Runnable> drain = new LinkedList<>(pending);
            pending.clear();
            drain.forEach(Runnable::run);
        }
    }

    private static String encode(
        float[][] vectors)
    {
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < vectors.length; i++)
        {
            if (i > 0)
            {
                encoded.append(VECTOR_DELIMITER);
            }

            float[] vector = vectors[i];
            if (vector == null)
            {
                encoded.append(NULL_VECTOR_TOKEN);
            }
            else
            {
                for (int j = 0; j < vector.length; j++)
                {
                    if (j > 0)
                    {
                        encoded.append(COMPONENT_DELIMITER);
                    }
                    encoded.append(vector[j]);
                }
            }
        }
        return encoded.toString();
    }

    private float[][] decode(
        String encoded)
    {
        String[] parts = encoded.split(VECTOR_DELIMITER, -1);
        float[][] vectors = parts.length == reject.size() ? new float[parts.length][] : null;

        if (vectors != null)
        {
            for (int i = 0; i < parts.length; i++)
            {
                String part = parts[i];
                vectors[i] = NULL_VECTOR_TOKEN.equals(part) ? null : asVector(part);
            }
        }

        return vectors;
    }

    private static float[] asVector(
        String part)
    {
        String[] components = part.split(COMPONENT_DELIMITER, -1);
        float[] vector = new float[components.length];
        for (int i = 0; i < components.length; i++)
        {
            vector[i] = Float.parseFloat(components[i]);
        }
        return vector;
    }

    private static String digest(
        List<String> reject)
    {
        StringBuilder canonical = new StringBuilder();
        for (String phrase : reject)
        {
            canonical.append(phrase.length()).append(':').append(phrase);
        }

        try
        {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
