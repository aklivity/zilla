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
package io.aklivity.zilla.runtime.exporter.otlp.config;

import java.net.URI;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class OtlpEndpointConfigBuilder<T> extends ConfigBuilder<T, OtlpEndpointConfigBuilder<T>>
{
    private static final String DEFAULT_PROTOCOL = "http";

    private final Function<OtlpEndpointConfig, T> mapper;

    private String protocol;
    private URI location;
    private OtlpOverridesConfig overrides;

    OtlpEndpointConfigBuilder(
        Function<OtlpEndpointConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OtlpEndpointConfigBuilder<T>> thisType()
    {
        return (Class<OtlpEndpointConfigBuilder<T>>) getClass();
    }

    public OtlpEndpointConfigBuilder<T> protocol(
        String protocol)
    {
        this.protocol = protocol;
        return this;
    }

    public OtlpEndpointConfigBuilder<T> location(
        URI location)
    {
        this.location = location;
        return this;
    }

    public OtlpOverridesConfigBuilder<OtlpEndpointConfigBuilder<T>> overrides()
    {
        return OtlpOverridesConfig.builder(this::overrides);
    }

    public OtlpEndpointConfigBuilder<T> overrides(
        OtlpOverridesConfig overrides)
    {
        this.overrides = overrides;
        return this;
    }

    @Override
    public T build()
    {
        final String protocol = this.protocol != null ? this.protocol : DEFAULT_PROTOCOL;
        return mapper.apply(new OtlpEndpointConfig(protocol, location, overrides));
    }
}
