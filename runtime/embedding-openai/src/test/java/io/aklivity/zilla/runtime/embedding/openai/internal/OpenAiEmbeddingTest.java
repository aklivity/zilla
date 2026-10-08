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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;

public class OpenAiEmbeddingTest
{
    @Test
    public void shouldResolveName()
    {
        OpenAiEmbedding embedding = new OpenAiEmbedding(new Configuration());

        assertThat(embedding.name(), equalTo("openai"));
    }

    @Test
    public void shouldSupplyContext()
    {
        OpenAiEmbedding embedding = new OpenAiEmbedding(new Configuration());

        assertThat(embedding.supply(mock(EngineContext.class)), instanceOf(OpenAiEmbeddingContext.class));
    }
}
