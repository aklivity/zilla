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
package io.aklivity.zilla.config.guard.jwt;

import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class JwtKeySetConfigBuilder<T> extends ConfigBuilder<T, JwtKeySetConfigBuilder<T>>
{
    private final Function<JwtKeySetConfig, T> mapper;

    private List<JwtKeyConfig> keys;

    JwtKeySetConfigBuilder(
        Function<JwtKeySetConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<JwtKeySetConfigBuilder<T>> thisType()
    {
        return (Class<JwtKeySetConfigBuilder<T>>) getClass();
    }

    public JwtKeySetConfigBuilder<T> keys(
        List<JwtKeyConfig> keys)
    {
        this.keys = keys;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new JwtKeySetConfig(keys));
    }
}
