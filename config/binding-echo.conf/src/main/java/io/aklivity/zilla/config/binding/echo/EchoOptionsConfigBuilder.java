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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class EchoOptionsConfigBuilder<T> extends ConfigBuilder<T, EchoOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private ModelConfig value;

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

    public EchoOptionsConfigBuilder<T> value(
        ModelConfig value)
    {
        this.value = value;
        return this;
    }

    @Override
    public T build()
    {
        List<ModelConfig> models = resolveModels(value);
        return mapper.apply(new EchoOptionsConfig(value, models, refs(models)));
    }

    private static List<ModelConfig> resolveModels(
        ModelConfig value)
    {
        return value != null ? singletonList(value) : emptyList();
    }

    private static List<NamedConfig> refs(
        List<ModelConfig> models)
    {
        List<NamedConfig> refs = new ArrayList<>();
        for (ModelConfig model : models)
        {
            refs.addAll(model.refs());
        }
        return refs;
    }
}
