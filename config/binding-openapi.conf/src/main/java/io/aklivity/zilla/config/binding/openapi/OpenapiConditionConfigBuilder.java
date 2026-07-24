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
package io.aklivity.zilla.config.binding.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;
import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class OpenapiConditionConfigBuilder<T> extends ConfigBuilder<T, OpenapiConditionConfigBuilder<T>>
{
    private final Function<ConditionConfig, T> mapper;

    private String spec;
    private String operation;
    private String tag;
    private List<OpenapiConditionServerConfig> servers;

    OpenapiConditionConfigBuilder(
        Function<ConditionConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<OpenapiConditionConfigBuilder<T>> thisType()
    {
        return (Class<OpenapiConditionConfigBuilder<T>>) getClass();
    }

    public OpenapiConditionConfigBuilder<T> spec(
        String spec)
    {
        this.spec = spec;
        return this;
    }

    public OpenapiConditionConfigBuilder<T> operation(
        String operation)
    {
        this.operation = operation;
        return this;
    }

    public OpenapiConditionConfigBuilder<T> tag(
        String tag)
    {
        this.tag = tag;
        return this;
    }

    public OpenapiConditionServerConfigBuilder<OpenapiConditionConfigBuilder<T>> server()
    {
        return OpenapiConditionServerConfig.builder(this::server);
    }

    public OpenapiConditionConfigBuilder<T> server(
        OpenapiConditionServerConfig server)
    {
        if (servers == null)
        {
            servers = new ArrayList<>();
        }
        servers.add(server);
        return this;
    }

    public OpenapiConditionConfigBuilder<T> servers(
        List<OpenapiConditionServerConfig> servers)
    {
        this.servers = servers;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new OpenapiConditionConfig(spec, operation, tag, servers));
    }
}
