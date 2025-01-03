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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.config.ConfigBuilder;

public final class KafkaTopicTransformsConfigBuilder<T> extends ConfigBuilder<T, KafkaTopicTransformsConfigBuilder<T>>
{
    private static final String PATH = "^\\$\\{message\\.(key|value)\\.([A-Za-z_][A-Za-z0-9_]*)\\}$";
    private static final Pattern PATH_PATTERN = Pattern.compile(PATH);
    private static final String INTERNAL_VALUE = "$.%s";
    private static final String INTERNAL_PATH = "^\\$\\..*$";
    private static final Pattern INTERNAL_PATH_PATTERN = Pattern.compile(INTERNAL_PATH);

    private final Matcher matcher;
    private final Matcher internalMatcher;
    private final Function<KafkaTopicTransformsConfig, T> mapper;
    private String extractKey;
    private List<KafkaTopicHeaderType> extractHeaders;

    KafkaTopicTransformsConfigBuilder(
        Function<KafkaTopicTransformsConfig, T> mapper)
    {
        this.mapper = mapper;
        this.extractHeaders = new ArrayList<>();
        this.matcher = PATH_PATTERN.matcher("");
        this.internalMatcher = INTERNAL_PATH_PATTERN.matcher("");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<KafkaTopicTransformsConfigBuilder<T>> thisType()
    {
        return (Class<KafkaTopicTransformsConfigBuilder<T>>) getClass();
    }

    public KafkaTopicTransformsConfigBuilder<T> extractKey(
        String extractKey)
    {
        this.extractKey = extractKey != null && matcher.reset(extractKey).matches()
            ? String.format(INTERNAL_VALUE, matcher.group(2))
            : extractKey;
        return this;
    }

    public KafkaTopicTransformsConfigBuilder<T> extractHeaders(
        List<KafkaTopicHeaderType> extractHeaders)
    {
        if (extractHeaders != null)
        {
            extractHeaders.forEach(this::extractHeader);
        }
        return this;
    }

    public KafkaTopicTransformsConfigBuilder<T> extractHeader(
        KafkaTopicHeaderType header)
    {
        return extractHeader(header.name, header.path, header.externalPath);
    }

    public KafkaTopicTransformsConfigBuilder<T> extractHeader(
        String name,
        String path)
    {
        return extractHeader(name, path, path);
    }

    private KafkaTopicTransformsConfigBuilder<T> extractHeader(
        String name,
        String path,
        String externalPath)
    {
        if (this.extractHeaders == null)
        {
            this.extractHeaders = new ArrayList<>();
        }
        if (matcher.reset(path).matches())
        {
            this.extractHeaders.add(new KafkaTopicHeaderType(name,
                String.format(INTERNAL_VALUE, matcher.group(2)), externalPath));
        }
        else if (internalMatcher.reset(path).matches())
        {
            this.extractHeaders.add(new KafkaTopicHeaderType(name, path, externalPath));
        }
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new KafkaTopicTransformsConfig(extractKey, extractHeaders));
    }
}
