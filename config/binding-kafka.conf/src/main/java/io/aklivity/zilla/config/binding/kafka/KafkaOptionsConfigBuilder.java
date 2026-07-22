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
package io.aklivity.zilla.config.binding.kafka;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.OptionsConfig;

public final class KafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, KafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private List<String> bootstrap;
    private List<KafkaTopicConfig> topics;
    private List<KafkaServerConfig> servers;
    private KafkaSaslConfig sasl;
    private KafkaAuthorizationConfig authorization;

    KafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.topics = new ArrayList<>();
        this.servers = new ArrayList<>();
        this.bootstrap = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaOptionsConfigBuilder<T>> thisType()
    {
        return (Class<KafkaOptionsConfigBuilder<T>>) getClass();
    }

    public KafkaOptionsConfigBuilder<T> bootstrap(
        String topic)
    {
        if (bootstrap == null)
        {
            bootstrap = new LinkedList<>();
        }
        bootstrap.add(topic);
        return this;
    }

    public KafkaOptionsConfigBuilder<T> topic(
        KafkaTopicConfig topic)
    {
        if (topics == null)
        {
            topics = new LinkedList<>();
        }
        topics.add(topic);
        return this;
    }

    public KafkaTopicConfigBuilder<KafkaOptionsConfigBuilder<T>> topic()
    {
        return KafkaTopicConfig.builder(this::topic);
    }

    public KafkaServerConfigBuilder<KafkaOptionsConfigBuilder<T>> server()
    {
        return KafkaServerConfig.builder(this::server);
    }

    public KafkaOptionsConfigBuilder<T> sasl(
        KafkaSaslConfig sasl)
    {
        this.sasl = sasl;
        return this;
    }

    public KafkaSaslConfigBuilder<KafkaOptionsConfigBuilder<T>> sasl()
    {
        return KafkaSaslConfig.builder(this::sasl);
    }

    public KafkaOptionsConfigBuilder<T> authorization(
        KafkaAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public KafkaAuthorizationConfigBuilder<KafkaOptionsConfigBuilder<T>> authorization()
    {
        return KafkaAuthorizationConfig.builder(this::authorization);
    }

    public KafkaOptionsConfigBuilder<T> server(
        KafkaServerConfig server)
    {
        if (servers == null)
        {
            servers = new LinkedList<>();
        }

        servers.add(server);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaOptionsConfig(bootstrap, topics, servers, sasl, authorization));
    }
}
