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
package io.aklivity.zilla.runtime.converter.core.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public class StringConverterConfigBuilder<T> extends ConfigBuilder<T, StringConverterConfigBuilder<T>>
{
    private final Function<StringConverterConfig, T> mapper;

    private String encoding;

    StringConverterConfigBuilder(
        Function<StringConverterConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<StringConverterConfigBuilder<T>> thisType()
    {
        return (Class<StringConverterConfigBuilder<T>>) getClass();
    }

    public StringConverterConfigBuilder<T> encoding(
        String encoding)
    {
        this.encoding = encoding;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new StringConverterConfig(encoding));
    }
}
