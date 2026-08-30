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
package io.aklivity.zilla.runtime.engine.test.internal.embedding;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.embedding.Embedding;
import io.aklivity.zilla.runtime.engine.embedding.EmbeddingContext;

public class TestEmbedding implements Embedding
{
    public static final String NAME = "test";

    public TestEmbedding(
        Configuration config)
    {
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public EmbeddingContext supply(
        EngineContext context)
    {
        return new TestEmbeddingContext(context);
    }
}
