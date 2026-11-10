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
package io.aklivity.zilla.runtime.embedding.openai.internal;

import java.net.http.HttpClient;
import java.util.function.Supplier;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfig;
import io.aklivity.zilla.config.engine.EmbeddingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

final class OpenaiEmbeddingContext implements EmbeddingContext
{
    private final EngineContext context;
    private final Supplier<HttpClient> supplyClient;
    private final Long2ObjectHashMap<OpenaiEmbeddingHandler> handlersById;

    private HttpClient client;

    OpenaiEmbeddingContext(
        EngineContext context)
    {
        this(context, HttpClient::newHttpClient);
    }

    OpenaiEmbeddingContext(
        EngineContext context,
        Supplier<HttpClient> supplyClient)
    {
        this.context = context;
        this.supplyClient = supplyClient;
        this.handlersById = new Long2ObjectHashMap<>();
    }

    @Override
    public EmbeddingHandler attach(
        EmbeddingConfig embedding)
    {
        OpenaiOptionsConfig options = (OpenaiOptionsConfig) embedding.options;
        if (client == null)
        {
            client = supplyClient.get();
        }
        OpenaiEmbeddingHandler handler = new OpenaiEmbeddingHandler(context, client, options);
        handlersById.put(embedding.id, handler);
        return handler;
    }

    @Override
    public void detach(
        EmbeddingConfig embedding)
    {
        OpenaiEmbeddingHandler handler = handlersById.remove(embedding.id);
        if (handler != null)
        {
            handler.close();
        }

        if (handlersById.isEmpty() && client != null)
        {
            client.shutdownNow();
            client = null;
        }
    }
}
