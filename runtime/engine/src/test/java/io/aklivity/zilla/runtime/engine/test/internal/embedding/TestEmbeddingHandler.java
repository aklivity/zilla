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
package io.aklivity.zilla.runtime.engine.test.internal.embedding;

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public final class TestEmbeddingHandler implements EmbeddingHandler
{
    private static final int DIMENSIONS = 8;
    private static final int SEED = 0x9e3779b9;

    private final Consumer<Runnable> dispatcher;

    public TestEmbeddingHandler(
        Consumer<Runnable> dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    @Override
    public void embed(
        long traceId,
        long bindingId,
        long contextId,
        String text,
        CompletionCallback completion)
    {
        dispatcher.accept(() -> complete(contextId, text, completion));
    }

    private void complete(
        long contextId,
        String text,
        CompletionCallback completion)
    {
        try
        {
            completion.completed(contextId, generate(text));
        }
        catch (Throwable ex)
        {
            completion.failed(contextId, ex);
        }
    }

    static float[] generate(
        String text)
    {
        float[] vector = null;

        if (text != null)
        {
            vector = new float[DIMENSIONS];
            int hash = text.hashCode();
            for (int i = 0; i < DIMENSIONS; i++)
            {
                int mixed = mix(hash, i);
                vector[i] = (mixed % 1000) / 1000.0f;
            }
        }

        return vector;
    }

    private static int mix(
        int hash,
        int index)
    {
        int mixed = hash ^ (index * SEED);
        mixed ^= mixed >>> 16;
        mixed *= 0x85ebca6b;
        mixed ^= mixed >>> 13;
        return mixed;
    }
}
