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
package io.aklivity.zilla.config.binding.mcp.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.binding.kafka.KafkaAuthorizationConfig;
import io.aklivity.zilla.config.binding.kafka.KafkaAuthorizationConfigBuilder;
import io.aklivity.zilla.config.binding.kafka.KafkaServerConfig;
import io.aklivity.zilla.config.binding.kafka.KafkaServerConfigBuilder;
import io.aklivity.zilla.config.binding.kafka.KafkaTopicConfig;
import io.aklivity.zilla.config.binding.kafka.KafkaTopicConfigBuilder;
import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class McpKafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, McpKafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private List<KafkaServerConfig> servers;
    private KafkaAuthorizationConfig authorization;
    private List<KafkaTopicConfig> topics;

    McpKafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.servers = new ArrayList<>();
        this.topics = new ArrayList<>();
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

    public McpKafkaOptionsConfigBuilder<T> topic(
        KafkaTopicConfig topic)
    {
        topics.add(topic);
        return this;
    }

    public KafkaTopicConfigBuilder<McpKafkaOptionsConfigBuilder<T>> topic()
    {
        return KafkaTopicConfig.builder(this::topic);
    }

    @Override
    public T build()
    {
        return mapper.apply(new McpKafkaOptionsConfig(servers, authorization, topics));
    }
}
