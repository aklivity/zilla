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

import io.aklivity.zilla.config.engine.factory.Aliasable;
import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Entry point for a text-embedding plugin.
 * <p>
 * An {@code Embedding} turns a string of text into a fixed-size numeric vector (an embedding),
 * for use by consumers that need semantic similarity (e.g. cosine-similarity ranking) rather
 * than exact-match lookup. Implementations are vendor- or technique-specific (e.g. a
 * locally-hosted transformer model, or a remote vendor API).
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through
 * {@link EmbeddingFactorySpi}. An embedding may declare aliases via {@link Aliasable#aliases()}
 * to support multiple configuration names.
 * </p>
 *
 * @see EmbeddingContext
 * @see EmbeddingHandler
 * @see EmbeddingFactorySpi
 */
public interface Embedding extends Aliasable
{
    /**
     * Returns the unique name identifying this embedding type, e.g. {@code "transformer"}.
     *
     * @return the embedding type name
     */
    String name();

    /**
     * Creates a per-thread context for this embedding.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link EmbeddingContext} confined to that thread
     */
    EmbeddingContext supply(
        EngineContext context);
}
