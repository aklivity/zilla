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
package io.aklivity.zilla.config.model.core;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ValidateConfig;

public class BytesModelConfigBuilder<T> extends ConfigBuilder.Extensible<T, BytesModelConfigBuilder<T>>
{
    private final Function<BytesModelConfig, T> mapper;

    private ValidateConfig validate;

    BytesModelConfigBuilder(
        Function<BytesModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<BytesModelConfigBuilder<T>> thisType()
    {
        return (Class<BytesModelConfigBuilder<T>>) getClass();
    }

    public BytesModelConfigBuilder<T> validate(
        ValidateConfig validate)
    {
        this.validate = validate;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new BytesModelConfig(validate, extensions()));
    }
}
