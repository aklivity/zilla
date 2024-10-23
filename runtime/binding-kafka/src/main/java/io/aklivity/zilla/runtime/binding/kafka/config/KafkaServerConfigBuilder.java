/*
 * Copyright 2021-2024 Aklivity Inc.
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


import java.util.function.Function;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class KafkaServerConfigBuilder<T> extends ConfigBuilder<T, KafkaServerConfigBuilder<T>>
{
    private final Function<KafkaServerConfig, T> mapper;
    private String host;
    private int port;

    KafkaServerConfigBuilder(
        Function<KafkaServerConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaServerConfigBuilder<T>> thisType()
    {
        return (Class<KafkaServerConfigBuilder<T>>) getClass();
    }

    public KafkaServerConfigBuilder<T> host(
        String host)
    {
        this.host = host;
        return this;
    }

    public KafkaServerConfigBuilder<T> port(
        int port)
    {
        this.port = port;
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaServerConfig(host, port));
    }
}
