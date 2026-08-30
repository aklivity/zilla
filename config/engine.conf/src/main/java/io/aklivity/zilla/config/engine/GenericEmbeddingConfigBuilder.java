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
package io.aklivity.zilla.config.engine;

import java.util.function.Function;

public final class GenericEmbeddingConfigBuilder<T> extends EmbeddingConfigBuilder<T, GenericEmbeddingConfigBuilder<T>>
{
    GenericEmbeddingConfigBuilder(
        Function<EmbeddingConfig, T> mapper)
    {
        super(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GenericEmbeddingConfigBuilder<T>> thisType()
    {
        return (Class<GenericEmbeddingConfigBuilder<T>>) getClass();
    }

    @Override
    public GenericEmbeddingConfigBuilder<T> type(
        String type)
    {
        return super.type(type);
    }

    @Override
    public <C extends ConfigBuilder<GenericEmbeddingConfigBuilder<T>, C>> C options(
        Function<Function<OptionsConfig, GenericEmbeddingConfigBuilder<T>>, C> options)
    {
        return super.options(options);
    }

    @Override
    protected EmbeddingConfig newEmbedding(
        String namespace,
        String name,
        String type,
        String vault,
        OptionsConfig options)
    {
        return new GenericEmbeddingConfig(namespace, name, type, vault, options);
    }
}
