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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String32FW;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class KafkaTopicConfigBuilder<T> extends ConfigBuilder<T, KafkaTopicConfigBuilder<T>>
{
    private final Function<KafkaTopicConfig, T> mapper;
    private String name;
    private KafkaOffsetType defaultOffset;
    private KafkaDeltaType deltaType;
    private ModelConfig key;
    private ModelConfig value;
    private List<KafkaTopicHeaderType> headers;

    KafkaTopicConfigBuilder(
        Function<KafkaTopicConfig, T> mapper)
    {
        this.mapper = mapper;
        this.headers = new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaTopicConfigBuilder<T>> thisType()
    {
        return (Class<KafkaTopicConfigBuilder<T>>) getClass();
    }

    public KafkaTopicConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public KafkaTopicConfigBuilder<T> defaultOffset(
        KafkaOffsetType defaultOffset)
    {
        this.defaultOffset = defaultOffset;
        return this;
    }

    public KafkaTopicConfigBuilder<T> deltaType(
        KafkaDeltaType deltaType)
    {
        this.deltaType = deltaType;
        return this;
    }

    public KafkaTopicConfigBuilder<T> key(
        ModelConfig key)
    {
        this.key = key;
        return this;
    }

    public KafkaTopicConfigBuilder<T> value(
        ModelConfig value)
    {
        this.value = value;
        return this;
    }

    public KafkaTopicConfigBuilder<T> header(
        String name,
        String path)
    {
        if (this.headers == null)
        {
            this.headers = new ArrayList<>();
        }
        this.headers.add(new KafkaTopicHeaderType(new String32FW(name, UTF_8), path));
        return this;
    }

    public <C extends ConfigBuilder<KafkaTopicConfigBuilder<T>, C>> C value(
        Function<Function<ModelConfig, KafkaTopicConfigBuilder<T>>, C> value)
    {
        return value.apply(this::value);
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaTopicConfig(name, defaultOffset, deltaType, key, value, headers));
    }
}
