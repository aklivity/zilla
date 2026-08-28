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
package io.aklivity.zilla.runtime.embedding.djl.internal;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public final class DjlEmbeddingHandler implements EmbeddingHandler, AutoCloseable
{
    static final String MODEL_URLS =
        "djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2/0.0.1";

    private final EngineContext context;
    private final ExecutorService executor;
    private final ZooModel<String, float[]> model;
    private final Predictor<String, float[]> predictor;

    public DjlEmbeddingHandler(
        EngineContext context)
    {
        this.context = context;
        this.model = load(MODEL_URLS);
        this.predictor = model.newPredictor();
        this.executor = Executors.newSingleThreadExecutor(DjlEmbeddingHandler::newDaemonThread);
    }

    @Override
    public void embed(
        long traceId,
        long bindingId,
        long contextId,
        String text,
        CompletionCallback completion)
    {
        executor.execute(() -> predict(contextId, text, completion));
    }

    @Override
    public void close()
    {
        executor.shutdown();
        predictor.close();
        model.close();
    }

    private void predict(
        long contextId,
        String text,
        CompletionCallback completion)
    {
        float[] result = null;
        Throwable failure = null;

        try
        {
            result = predictor.predict(text);
        }
        catch (TranslateException ex)
        {
            failure = ex;
        }

        final float[] embedding = result;
        final Throwable cause = failure;
        context.dispatch(() -> complete(contextId, embedding, cause, completion));
    }

    private static void complete(
        long contextId,
        float[] embedding,
        Throwable cause,
        CompletionCallback completion)
    {
        if (cause != null)
        {
            completion.failed(contextId, cause);
        }
        else
        {
            completion.completed(contextId, embedding);
        }
    }

    private static ZooModel<String, float[]> load(
        String modelUrls)
    {
        ZooModel<String, float[]> model;
        try
        {
            model = newCriteria(modelUrls).loadModel();
        }
        catch (IOException | ModelNotFoundException | MalformedModelException ex)
        {
            throw new IllegalStateException(ex);
        }

        return model;
    }

    static Criteria<String, float[]> newCriteria(
        String modelUrls)
    {
        return Criteria.builder()
            .setTypes(String.class, float[].class)
            .optModelUrls(modelUrls)
            .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
            .optEngine("PyTorch")
            .build();
    }

    private static Thread newDaemonThread(
        Runnable task)
    {
        Thread thread = new Thread(task, "embedding-djl");
        thread.setDaemon(true);
        return thread;
    }
}
