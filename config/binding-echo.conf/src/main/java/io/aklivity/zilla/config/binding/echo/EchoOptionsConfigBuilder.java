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
package io.aklivity.zilla.config.binding.echo;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class EchoOptionsConfigBuilder<T> extends ConfigBuilder<T, EchoOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String embedding;
    private List<String> reject;
    private double threshold;

    EchoOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<EchoOptionsConfigBuilder<T>> thisType()
    {
        return (Class<EchoOptionsConfigBuilder<T>>) getClass();
    }

    public EchoOptionsConfigBuilder<T> embedding(
        String embedding)
    {
        this.embedding = embedding;
        return this;
    }

    public EchoOptionsConfigBuilder<T> reject(
        List<String> reject)
    {
        this.reject = reject;
        return this;
    }

    public EchoOptionsConfigBuilder<T> threshold(
        double threshold)
    {
        this.threshold = threshold;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new EchoOptionsConfig(embedding, reject, threshold));
    }
}
