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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;

public final class KafkaTopicConfigBuilder<T> extends ConfigBuilder<T, KafkaTopicConfigBuilder<T>>
{
    private static final String PATH = "^\\$\\{message\\.value\\.([A-Za-z_][A-Za-z0-9_]*)\\}$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);
    private static final String INTERNAL_VALUE = "$.%s";
    private static final String INTERNAL_PATH = "^\\$\\..*$";
    private static final Pattern INTERNAL_PATH_PATTERN = Pattern.compile(INTERNAL_PATH);

    private final Matcher matcher;
    private final Matcher internalMatcher;
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
        this.matcher = PATH_PATTERN.matcher("");
        this.internalMatcher = INTERNAL_PATH_PATTERN.matcher("");
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

    public KafkaTopicConfigBuilder<T> headers(
        List<KafkaTopicHeaderType> headers)
    {
        if (headers != null)
        {
            headers.forEach(this::header);
        }
        return this;
    }

    public KafkaTopicConfigBuilder<T> header(
        KafkaTopicHeaderType header)
    {
        return header(header.name, header.path);
    }

    public KafkaTopicConfigBuilder<T> header(
        String name,
        String path)
    {
        if (this.headers == null)
        {
            this.headers = new ArrayList<>();
        }
        if (matcher.reset(path).matches())
        {
            this.headers.add(new KafkaTopicHeaderType(name,
                String.format(INTERNAL_VALUE, matcher.group(1))));
        }
        else if (internalMatcher.reset(path).matches())
        {
            this.headers.add(new KafkaTopicHeaderType(name, path));
        }
        return this;
    }

    public <C extends ConfigBuilder<KafkaTopicConfigBuilder<T>, C>> C key(
        Function<Function<ModelConfig, KafkaTopicConfigBuilder<T>>, C> key)
    {
        return key.apply(this::key);
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
