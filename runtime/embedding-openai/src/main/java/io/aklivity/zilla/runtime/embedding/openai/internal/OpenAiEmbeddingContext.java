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

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.embedding.openai.OpenAiOptionsConfig;
import io.aklivity.zilla.config.engine.EmbeddingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

final class OpenAiEmbeddingContext implements EmbeddingContext
{
    private final EngineContext context;
    private final OpenAiConfiguration config;
    private final HttpClient client;
    private final Long2ObjectHashMap<OpenAiEmbeddingHandler> handlersById;

    OpenAiEmbeddingContext(
        EngineContext context,
        OpenAiConfiguration config)
    {
        this.context = context;
        this.config = config;
        this.client = HttpClient.newHttpClient();
        this.handlersById = new Long2ObjectHashMap<>();
    }

    @Override
    public EmbeddingHandler attach(
        EmbeddingConfig embedding)
    {
        OpenAiOptionsConfig options = (OpenAiOptionsConfig) embedding.options;
        OpenAiEmbeddingHandler handler = new OpenAiEmbeddingHandler(context, config, client, options);
        handlersById.put(embedding.id, handler);
        return handler;
    }

    @Override
    public void detach(
        EmbeddingConfig embedding)
    {
        OpenAiEmbeddingHandler handler = handlersById.remove(embedding.id);
        if (handler != null)
        {
            handler.close();
        }
    }
}
