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
package io.aklivity.zilla.runtime.engine.embedding;

import io.aklivity.zilla.config.engine.EmbeddingConfig;

/**
 * Per-thread context for a text-embedding plugin.
 * <p>
 * Created once per I/O thread by {@link Embedding#supply(EngineContext)} and confined to that
 * thread. Manages the lifecycle of {@link EmbeddingHandler} instances for embedding
 * configurations active on this thread.
 * </p>
 *
 * @see Embedding
 * @see EmbeddingHandler
 */
public interface EmbeddingContext
{
    /**
     * Attaches an embedding configuration to this thread's context.
     *
     * @param embedding  the embedding configuration to activate
     * @return an {@link EmbeddingHandler} for producing embedding vectors,
     *         or {@code null} if this embedding has no per-binding handler
     */
    default EmbeddingHandler attach(
        EmbeddingConfig embedding)
    {
        return null;
    }

    /**
     * Detaches a previously attached embedding configuration, releasing associated resources.
     *
     * @param embedding  the embedding configuration to deactivate
     */
    default void detach(
        EmbeddingConfig embedding)
    {
    }
}
