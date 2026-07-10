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
package io.aklivity.zilla.runtime.binding.mcp.kafka.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaAuthorizationConfigBuilder;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class McpKafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, McpKafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private List<KafkaServerConfig> servers;
    private KafkaAuthorizationConfig authorization;

    McpKafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.servers = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<McpKafkaOptionsConfigBuilder<T>> thisType()
    {
        return (Class<McpKafkaOptionsConfigBuilder<T>>) getClass();
    }

    public McpKafkaOptionsConfigBuilder<T> server(
        KafkaServerConfig server)
    {
        servers.add(server);
        return this;
    }

    public KafkaServerConfigBuilder<McpKafkaOptionsConfigBuilder<T>> server()
    {
        return KafkaServerConfig.builder(this::server);
    }

    public McpKafkaOptionsConfigBuilder<T> authorization(
        KafkaAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public KafkaAuthorizationConfigBuilder<McpKafkaOptionsConfigBuilder<T>> authorization()
    {
        return KafkaAuthorizationConfig.builder(this::authorization);
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpKafkaOptionsConfig(servers, authorization));
    }
}
