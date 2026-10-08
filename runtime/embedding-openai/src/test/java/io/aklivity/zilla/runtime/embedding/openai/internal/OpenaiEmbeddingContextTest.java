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

    private static EmbeddingConfig embeddingConfig()
    {
        OpenaiOptionsConfig options = OpenaiOptionsConfig.builder()
            .model("text-embedding-3-small")
            .credentials(OpenaiCredentialsConfig.builder()
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
