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
package io.aklivity.zilla.config.binding.mqtt;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.aklivity.zilla.config.engine.ConfigBuilder;
import io.aklivity.zilla.config.engine.ModelConfig;
import io.aklivity.zilla.config.engine.NamedConfig;
import io.aklivity.zilla.config.engine.OptionsConfig;

public class MqttOptionsConfigBuilder<T> extends ConfigBuilder<T, MqttOptionsConfigBuilder<T>>
{
    private final Function<OptionsConfig, T> mapper;

    private MqttAuthorizationConfig authorization;
    private List<MqttTopicConfig> topics;
    private List<MqttVersion> versions;
    private String store;
    private String server;

    MqttOptionsConfigBuilder(
        Function<OptionsConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<MqttOptionsConfigBuilder<T>> thisType()
    {
        return (Class<MqttOptionsConfigBuilder<T>>) getClass();
    }


    public MqttOptionsConfigBuilder<T> topics(
        List<MqttTopicConfig> topics)
    {
        if (topics == null)
        {
            topics = new LinkedList<>();
        }
        this.topics = topics;
        return this;
    }

    public MqttOptionsConfigBuilder<T> topic(
        MqttTopicConfig topic)
    {
        if (this.topics == null)
        {
            this.topics = new LinkedList<>();
        }
        this.topics.add(topic);
        return this;
    }

    public MqttOptionsConfigBuilder<T> versions(
        List<MqttVersion> versions)
    {
        if (versions == null)
        {
            versions = new LinkedList<>();
        }
        this.versions = versions;
        return this;
    }

    public MqttOptionsConfigBuilder<T> version(
        MqttVersion version)
    {
        if (this.versions == null)
        {
            this.versions = new LinkedList<>();
        }
        this.versions.add(version);
        return this;
    }

    public MqttTopicConfigBuilder<MqttOptionsConfigBuilder<T>> topic()
    {
        return new MqttTopicConfigBuilder<>(this::topic);
    }

    public MqttOptionsConfigBuilder<T> authorization(
        MqttAuthorizationConfig authorization)
    {
        this.authorization = authorization;
        return this;
    }

    public MqttAuthorizationConfigBuilder<MqttOptionsConfigBuilder<T>> authorization()
    {
        return new MqttAuthorizationConfigBuilder<>(this::authorization);
    }

    public MqttOptionsConfigBuilder<T> store(
        String store)
    {
        this.store = store;
        return this;
    }

    public MqttOptionsConfigBuilder<T> server(
        String server)
    {
        this.server = server;
        return this;
    }

    @Override
    public T build()
    {
        List<ModelConfig> models = resolveModels(topics);
        return mapper.apply(new MqttOptionsConfig(authorization, topics, versions, store, server, models, refs(topics)));
    }

    private static List<ModelConfig> resolveModels(
        List<MqttTopicConfig> topics)
    {
        return topics != null && !topics.isEmpty()
            ? topics.stream()
            .flatMap(topic -> Stream.concat(
                    Stream.of(topic.content),
                    Optional.ofNullable(topic.userProperties).orElseGet(Collections::emptyList).stream()
                        .flatMap(p -> Stream.of(p.value))
                        .filter(Objects::nonNull))
                .filter(Objects::nonNull))
            .collect(Collectors.toList())
            : emptyList();
    }

    private static List<NamedConfig> refs(
        List<MqttTopicConfig> topics)
    {
        List<NamedConfig> refs = new ArrayList<>();
        if (topics != null)
        {
            for (MqttTopicConfig topic : topics)
            {
                refs.addAll(topic.refs());
            }
        }
        return refs;
    }
}
