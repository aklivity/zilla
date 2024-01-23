/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.config;

import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ValidatorConfig;

public class HttpParamConfigBuilder<T> extends ConfigBuilder<T, HttpParamConfigBuilder<T>>
{
    private final Function<HttpParamConfig, T> mapper;

    private String name;
    private ValidatorConfig validator;

    HttpParamConfigBuilder(
        Function<HttpParamConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<HttpParamConfigBuilder<T>> thisType()
    {
        return (Class<HttpParamConfigBuilder<T>>) getClass();
    }

    public HttpParamConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public HttpParamConfigBuilder<T> validator(
        ValidatorConfig validator)
    {
        this.validator = validator;
        return this;
    }

    public <C extends ConfigBuilder<HttpParamConfigBuilder<T>, C>> C validator(
        Function<Function<ValidatorConfig, HttpParamConfigBuilder<T>>, C> validator)
    {
        return validator.apply(this::validator);
    }

    @Override
    public T build()
    {
        return mapper.apply(new HttpParamConfig(name, validator));
    }
}
