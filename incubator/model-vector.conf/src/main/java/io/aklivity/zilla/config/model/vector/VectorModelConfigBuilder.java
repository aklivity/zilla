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
package io.aklivity.zilla.config.model.vector;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.EmbeddedConfig;

public class VectorModelConfigBuilder<T> extends ConfigBuilder.Extensible<T, VectorModelConfigBuilder<T>>
{
    private final Function<VectorModelConfig, T> mapper;

    private EmbeddedConfig embedding;
    private List<String> reject;
    private double threshold;

    VectorModelConfigBuilder(
        Function<VectorModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<VectorModelConfigBuilder<T>> thisType()
    {
        return (Class<VectorModelConfigBuilder<T>>) getClass();
    }

    public VectorModelConfigBuilder<T> embedding(
        String embedding)
    {
        this.embedding = embedding != null ? EmbeddedConfig.builder().name(embedding).build() : null;
        return this;
    }

    public VectorModelConfigBuilder<T> reject(
        List<String> reject)
    {
        this.reject = reject;
        return this;
    }

    public VectorModelConfigBuilder<T> reject(
        String reject)
    {
        if (this.reject == null)
        {
            this.reject = new LinkedList<>();
        }
        this.reject.add(reject);
        return this;
    }

    public VectorModelConfigBuilder<T> threshold(
        double threshold)
    {
        this.threshold = threshold;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new VectorModelConfig(embedding, reject, threshold, extensions(), refs()));
    }
}
