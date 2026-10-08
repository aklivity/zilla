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

import java.util.List;

import io.aklivity.zilla.runtime.common.vector.Vectors;
import io.aklivity.zilla.runtime.engine.classifier.Detector;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

final class SemanticDetector implements Detector
{
    private final EmbeddingHandler embedding;
    private final SemanticReferences references;
    private final double threshold;
    private final int[] exemplars;

    SemanticDetector(
        EmbeddingHandler embedding,
        SemanticReferences references,
        double threshold,
        int[] exemplars)
    {
        this.embedding = embedding;
        this.references = references;
        this.threshold = threshold;
        this.exemplars = exemplars;
    }

    @Override
    public void detect(
        long traceId,
        long bindingId,
        long contextId,
        String value,
        CompletionCallback completion)
    {
        references.whenReady(new Detection(traceId, bindingId, contextId, value, completion));
    }

    private boolean matches(
        float[][] vectors,
        float[] input)
    {
        boolean matched = false;

        for (int i = 0; !matched && i < exemplars.length; i++)
        {
            matched = Vectors.similarity(input, vectors[exemplars[i]]) >= threshold;
        }

        return matched;
    }

    private final class Detection implements SemanticReferences.Waiter, EmbeddingHandler.CompletionCallback
    {
        private final long traceId;
        private final long bindingId;
        private final long contextId;
        private final String value;
        private final CompletionCallback completion;

        private float[][] vectors;

        private Detection(
            long traceId,
            long bindingId,
            long contextId,
            String value,
            CompletionCallback completion)
        {
            this.traceId = traceId;
            this.bindingId = bindingId;
            this.contextId = contextId;
            this.value = value;
            this.completion = completion;
        }

        @Override
        public void ready(
            float[][] vectors)
        {
            this.vectors = vectors;
            embedding.embed(traceId, bindingId, contextId, List.of(value), this);
        }

        @Override
        public void failed(
            Throwable ex)
        {
            completion.failed(contextId, ex);
        }

        @Override
        public void completed(
            long contextId,
            float[][] results)
        {
            if (SemanticVectorCodec.valid(results, 1))
            {
                completion.completed(contextId, matches(vectors, results[0]));
            }
            else
            {
                completion.failed(contextId, new IllegalStateException("embedding did not return a vector for the value"));
            }
        }

        @Override
        public void failed(
            long contextId,
            Throwable ex)
        {
            completion.failed(contextId, ex);
        }
    }
}
