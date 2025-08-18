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

package io.aklivity.zilla.runtime.binding.grpc.config;


import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

import java.util.List;
import java.util.function.Function;

public final class GrpcOptionsConfigBuilder<T> extends ConfigBuilder<T, GrpcOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private List<String> services;

    GrpcOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<GrpcOptionsConfigBuilder<T>> thisType()
    {
        return (Class<GrpcOptionsConfigBuilder<T>>) getClass();
    }


    public GrpcOptionsConfigBuilder<T> services(
        List<String> services)
    {
        this.services = services;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new GrpcOptionsConfig(services));
    }
}
