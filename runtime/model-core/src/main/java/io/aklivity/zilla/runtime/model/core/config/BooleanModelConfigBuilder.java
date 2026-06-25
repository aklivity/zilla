/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.core.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ValidateConfig;

public class BooleanModelConfigBuilder<T> extends ConfigBuilder<T, BooleanModelConfigBuilder<T>>
{
    private final Function<BooleanModelConfig, T> mapper;

    private ValidateConfig validate;

    BooleanModelConfigBuilder(
        Function<BooleanModelConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<BooleanModelConfigBuilder<T>> thisType()
    {
        return (Class<BooleanModelConfigBuilder<T>>) getClass();
    }

    public BooleanModelConfigBuilder<T> validate(
        ValidateConfig validate)
    {
        this.validate = validate;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new BooleanModelConfig(validate));
    }
}
