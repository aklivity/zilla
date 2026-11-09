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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.embedding.openai.OpenaiCredentialsConfig;
import io.aklivity.zilla.config.embedding.openai.OpenaiOptionsConfig;
import io.aklivity.zilla.config.engine.EmbeddingConfig;
import io.aklivity.zilla.config.engine.GenericEmbeddingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public class OpenaiEmbeddingContextTest
{
    @Test
    public void shouldAttachAndDetachHandler()
    {
        OpenaiEmbeddingContext context = new OpenaiEmbeddingContext(mock(EngineContext.class));
        EmbeddingConfig embedding = embeddingConfig();

        EmbeddingHandler handler = context.attach(embedding);

        assertThat(handler, instanceOf(OpenaiEmbeddingHandler.class));

        context.detach(embedding);
    }

    @Test
    public void shouldTolerateDetachWithoutAttach()
    {
        OpenaiEmbeddingContext context = new OpenaiEmbeddingContext(mock(EngineContext.class));
        EmbeddingConfig embedding = embeddingConfig();

        context.detach(embedding);

        assertThat(context.attach(embedding), instanceOf(OpenaiEmbeddingHandler.class));
    }

    @Test
    public void shouldCloseHttpClientWhenLastEmbeddingDetached() throws Exception
    {
        List<HttpClient> clients = new ArrayList<>();
        OpenaiEmbeddingContext context = newContext(clients);
        EmbeddingConfig first = embeddingConfig(1L);
        EmbeddingConfig second = embeddingConfig(2L);

        context.attach(first);
        context.attach(second);
        context.detach(first);

        assertThat(clients.size(), is(1));
        assertThat(clients.get(0).isTerminated(), is(false));

        context.detach(second);

        assertThat(clients.get(0).awaitTermination(Duration.ofSeconds(5L)), is(true));
    }

    @Test
    public void shouldCreateHttpClientAgainWhenAttachedAfterLastEmbeddingDetached() throws Exception
    {
        List<HttpClient> clients = new ArrayList<>();
        OpenaiEmbeddingContext context = newContext(clients);
        EmbeddingConfig embedding = embeddingConfig(1L);

        context.attach(embedding);
        context.detach(embedding);
        context.attach(embedding);

        assertThat(clients.size(), is(2));
        assertThat(clients.get(0).awaitTermination(Duration.ofSeconds(5L)), is(true));
        assertThat(clients.get(1).isTerminated(), is(false));

        context.detach(embedding);

        assertThat(clients.get(1).awaitTermination(Duration.ofSeconds(5L)), is(true));
    }

    private static OpenaiEmbeddingContext newContext(
        List<HttpClient> clients)
    {
        return new OpenaiEmbeddingContext(mock(EngineContext.class), () ->
        {
            HttpClient client = HttpClient.newHttpClient();
            clients.add(client);
            return client;
        });
    }

    private static EmbeddingConfig embeddingConfig()
    {
        return embeddingConfig(1L);
    }

    private static EmbeddingConfig embeddingConfig(
        long id)
    {
        OpenaiOptionsConfig options = OpenaiOptionsConfig.builder()
            .model("text-embedding-3-small")
            .credentials(OpenaiCredentialsConfig.builder()
                .apiKey("test-key")
                .build())
            .build();

        GenericEmbeddingConfig embedding = GenericEmbeddingConfig.builder()
            .namespace("test")
            .name("embedding" + id)
            .type("openai")
            .options(options)
            .build();
        embedding.id = id;

        return embedding;
    }
}
