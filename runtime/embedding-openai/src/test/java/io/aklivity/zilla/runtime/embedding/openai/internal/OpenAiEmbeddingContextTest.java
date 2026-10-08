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
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.config.embedding.openai.OpenAiCredentialsConfig;
import io.aklivity.zilla.config.embedding.openai.OpenAiOptionsConfig;
import io.aklivity.zilla.config.engine.EmbeddingConfig;
import io.aklivity.zilla.config.engine.GenericEmbeddingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;

public class OpenAiEmbeddingContextTest
{
    @Test
    public void shouldAttachAndDetachHandler()
    {
        OpenAiEmbeddingContext context = new OpenAiEmbeddingContext(mock(EngineContext.class), new OpenAiConfiguration());
        EmbeddingConfig embedding = embeddingConfig();

        EmbeddingHandler handler = context.attach(embedding);

        assertThat(handler, instanceOf(OpenAiEmbeddingHandler.class));

        context.detach(embedding);
    }

    @Test
    public void shouldTolerateDetachWithoutAttach()
    {
        OpenAiEmbeddingContext context = new OpenAiEmbeddingContext(mock(EngineContext.class), new OpenAiConfiguration());
        EmbeddingConfig embedding = embeddingConfig();

        context.detach(embedding);

        assertThat(context.attach(embedding), instanceOf(OpenAiEmbeddingHandler.class));
    }

    private static EmbeddingConfig embeddingConfig()
    {
        OpenAiOptionsConfig options = OpenAiOptionsConfig.builder()
            .model("text-embedding-3-small")
            .credentials(OpenAiCredentialsConfig.builder()
                .apiKey("test-key")
                .build())
            .build();

        GenericEmbeddingConfig embedding = GenericEmbeddingConfig.builder()
            .namespace("test")
            .name("embedding0")
            .type("openai")
            .options(options)
            .build();
        embedding.id = 1L;

        return embedding;
    }
}
