/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.inline.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class InlineOptionsConfigBuilder<T> extends ConfigBuilder<T, InlineOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private List<InlineSchemaConfig> subjects;

    InlineOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    protected Class<InlineOptionsConfigBuilder<T>> thisType()
    {
        return (Class<InlineOptionsConfigBuilder<T>>) getClass();
    }

    public InlineSchemaConfigBuilder<InlineOptionsConfigBuilder<T>> subjects()
    {
        return new InlineSchemaConfigBuilder<>(this::subjects);
    }

    public InlineOptionsConfigBuilder<T> subjects(
        InlineSchemaConfig config)
    {
        if (subjects == null)
        {
            subjects = new ArrayList<>();
        }
        subjects.add(config);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new InlineOptionsConfig(subjects));
    }
}
