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
package io.aklivity.zilla.config.binding.agui;

import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class AguiOptionsConfigBuilder<T> extends ConfigBuilder<T, AguiOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private String server;
    private AguiAuthorizationConfig authorization;

    AguiOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<AguiOptionsConfigBuilder<T>> thisType()
    {
        return (Class<AguiOptionsConfigBuilder<T>>) getClass();
    }

    public AguiOptionsConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    public AguiOptionsConfigBuilder<T> authorization(
        AguiAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public AguiAuthorizationConfigBuilder<AguiOptionsConfigBuilder<T>> authorization()
    {
        return AguiAuthorizationConfig.builder(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new AguiOptionsConfig(server, authorization));
    }
}
