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
package io.aklivity.zilla.runtime.binding.kafka.config;


import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public final class KafkaOptionsConfigBuilder<T> extends ConfigBuilder<T, KafkaOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;
    private List<String> bootstrap;
    private List<KafkaTopicConfig> topics;
    private List<KafkaServerConfig> servers;
    private KafkaSaslConfig sasl;

    KafkaOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaOptionsConfigBuilder<T>> thisType()
    {
        return (Class<KafkaOptionsConfigBuilder<T>>) getClass();
    }

    public KafkaOptionsConfigBuilder<T> bootstrap(
        List<String> bootstrap)
    {
        this.bootstrap = bootstrap;
        return this;
    }

    public KafkaOptionsConfigBuilder<T> topics(
        List<KafkaTopicConfig> topics)
    {
        this.topics = topics;
        return this;
    }

    public KafkaOptionsConfigBuilder<T> servers(
        List<KafkaServerConfig> servers)
    {
        this.servers = servers;
        return this;
    }

    public KafkaOptionsConfigBuilder<T> sasl(
        KafkaSaslConfig sasl)
    {
        this.sasl = sasl;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaOptionsConfig(bootstrap, topics, servers, sasl));
    }
}
