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
package io.aklivity.zilla.runtime.types.avro.config;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class AvroConverterConfigBuilder<T> extends ConfigBuilder<T, AvroConverterConfigBuilder<T>>
{
    private final Function<AvroConverterConfig, T> mapper;

    private List<CatalogedConfig> catalogs;
    private String subject;
    private String format;

    AvroConverterConfigBuilder(
        Function<AvroConverterConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AvroConverterConfigBuilder<T>> thisType()
    {
        return (Class<AvroConverterConfigBuilder<T>>) getClass();
    }

    public AvroConverterConfigBuilder<T> subject(
        String subject)
    {
        this.subject = subject;
        return this;
    }

    public AvroConverterConfigBuilder<T> format(
        String format)
    {
        this.format = format;
        return this;
    }

    public CatalogedConfigBuilder<AvroConverterConfigBuilder<T>> catalog()
    {
        return CatalogedConfig.builder(this::catalog);
    }

    public AvroConverterConfigBuilder<T> catalog(
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
        return mapper.apply(new AvroConverterConfig(catalogs, subject, format));
    }
}
