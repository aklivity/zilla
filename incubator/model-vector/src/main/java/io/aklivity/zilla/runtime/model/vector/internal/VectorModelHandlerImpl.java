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

import java.util.LinkedList;
import java.util.List;

import io.aklivity.zilla.config.model.vector.VectorModelConfig;
import io.aklivity.zilla.runtime.common.vector.Vectors;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

// Per-worker factory for the vector model, resolving the named embedding once and embedding the
// configured reject phrases once, then vending a fresh per-stream VectorModelPipeline that reuses
// this handler's resolved embedding and reject vectors on every supplyDecoder/supplyEncoder call.
final class VectorModelHandlerImpl implements ModelHandler
{
    private static final Runnable NOOP = () ->
    {
    };

    private final EmbeddingHandler handler;
    private final float[][] rejectVectors;
    private final double threshold;
    private final List<Runnable> pending;

    private boolean ready;
    private int rejectVectorsReceived;

    VectorModelHandlerImpl(
        EngineContext context,
        VectorModelConfig config)
    {
        this.handler = context.supplyEmbedding(config.embedding.id);
        this.threshold = config.threshold;
        this.pending = new LinkedList<>();
        this.rejectVectors = new float[config.reject.size()][];

        for (int i = 0; i < config.reject.size(); i++)
        {
            final int index = i;
            handler.embed(0L, 0L, 0L, config.reject.get(i), new EmbeddingHandler.CompletionCallback()
            {
                @Override
                public void completed(
                    long contextId,
                    float[] result)
                {
                    onRejectVectorEmbedded(index, result);
                }

                @Override
                public void failed(
                    long contextId,
                    Throwable ex)
                {
                    onRejectVectorEmbedded(index, null);
                }
            });
        }
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
        handler.embed(traceId, bindingId, 0L, text, callback);
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
}
