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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.classifier.semantic.SemanticClassifierOptionsConfig;
import io.aklivity.zilla.config.engine.ClassifierConfig;
import io.aklivity.zilla.config.engine.GenericClassifierConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.classifier.ClassifierHandler;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingHandler;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public class SemanticClassifierContextTest
{
    private StoreHandler store;
    private SemanticClassifierContext context;
    private ClassifierConfig config;

    @Before
    public void init()
    {
        store = mock(StoreHandler.class);

        EngineContext engine = mock(EngineContext.class);
        when(engine.supplyEmbedding(1L)).thenReturn(mock(EmbeddingHandler.class));
        when(engine.supplyStore(2L)).thenReturn(store);
        when(engine.signaler()).thenReturn(mock(Signaler.class));

        config = GenericClassifierConfig.builder()
            .namespace("test")
            .name("moderator0")
            .type("semantic")
            .embedding("embedding0")
            .store("store0")
            .options(SemanticClassifierOptionsConfig.builder()
                .threshold(0.9)
                .label("first", List.of("alpha"))
                .build())
            .build();
        config.embeddingId = 1L;
        config.storeId = 2L;

        context = new SemanticClassifierContext(engine, new SemanticClassifierConfiguration(new Configuration()));
    }

    @Test
    public void shouldAttachHandler()
    {
        ClassifierHandler handler = context.attach(config);

        assertThat(handler, instanceOf(SemanticClassifierHandler.class));
        verify(store).get(anyString(), any());
    }

    @Test
    public void shouldDetachHandler()
    {
        context.attach(config);

        context.detach(config);

        assertThat(context.attach(config), instanceOf(SemanticClassifierHandler.class));
    }

    @Test
    public void shouldIgnoreDetachWhenNotAttached()
    {
        context.detach(config);

        assertThat(context.attach(config), instanceOf(SemanticClassifierHandler.class));
    }
}
