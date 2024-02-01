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
package io.aklivity.zilla.runtime.model.avro.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class AvroModelConfigBuilder<T> extends ConfigBuilder<T, AvroModelConfigBuilder<T>>
{
    private final Function<AvroModelConfig, T> mapper;

    private List<CatalogedConfig> catalogs;
    private String subject;
    private String view;

    AvroModelConfigBuilder(
        Function<AvroModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AvroModelConfigBuilder<T>> thisType()
    {
        return (Class<AvroModelConfigBuilder<T>>) getClass();
    }

    public AvroModelConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public AvroModelConfigBuilder<T> view(
        String view)
    {
        this.view = view;
        return this;
    }

    public CatalogedConfigBuilder<AvroModelConfigBuilder<T>> catalog()
    {
        return CatalogedConfig.builder(this::catalog);
    }

    public AvroModelConfigBuilder<T> catalog(
        CatalogedConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new AvroModelConfig(catalogs, subject, view));
    }
}
