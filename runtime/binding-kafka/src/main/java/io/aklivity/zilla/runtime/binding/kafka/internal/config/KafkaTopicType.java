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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicTransformsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;

public class KafkaTopicType
{
    public static final KafkaTopicType DEFAULT_TOPIC_TYPE = new KafkaTopicType();

    public final ConverterHandler keyReader;
    public final ConverterHandler keyWriter;
    public final ConverterHandler valueReader;
    public final ConverterHandler valueWriter;
    public final KafkaTopicTransformsConfig transforms;

    private final Matcher topicMatch;

    private KafkaTopicType()
    {
        this.topicMatch = null;
        this.keyReader = ConverterHandler.NONE;
        this.keyWriter = ConverterHandler.NONE;
        this.valueReader = ConverterHandler.NONE;
        this.valueWriter = ConverterHandler.NONE;
        this.transforms = null;
    }

    public KafkaTopicType(
        EngineContext context,
        KafkaTopicConfig topicConfig)
    {
        this.topicMatch = topicConfig.name != null ? asMatcher(topicConfig.name) : null;
        this.transforms = topicConfig.transforms;
        this.keyReader = Optional.ofNullable(topicConfig.key)
            .map(context::supplyReadConverter)
            .map(this::key)
            .map(this::headers)
            .orElse(ConverterHandler.NONE);
        this.keyWriter = Optional.ofNullable(topicConfig.key)
            .map(context::supplyWriteConverter)
            .orElse(ConverterHandler.NONE);
        this.valueReader = Optional.ofNullable(topicConfig.value)
            .map(context::supplyReadConverter)
            .map(this::key)
            .map(this::headers)
            .orElse(ConverterHandler.NONE);
        this.valueWriter = Optional.ofNullable(topicConfig.value)
            .map(context::supplyWriteConverter)
            .orElse(ConverterHandler.NONE);
    }

    public boolean matches(
        String topic)
    {
        return this.topicMatch == null || this.topicMatch.reset(topic).matches();
    }

    private ConverterHandler key(
        ConverterHandler handler)
    {
        if (transforms != null && transforms.extractKey != null)
        {
            handler.extract(transforms.extractKey);
        }
        return handler;
    }

    private ConverterHandler headers(
        ConverterHandler handler)
    {
        if (transforms != null && transforms.extractHeaders != null)
        {
            for (KafkaTopicHeaderType header : transforms.extractHeaders)
            {
                handler.extract(header.path);
            }
        }
        return handler;
    }

    private static Matcher asMatcher(
        String topic)
    {
        return Pattern.compile(topic.replaceAll("\\{[^}]+\\}", ".+")).matcher("");
    }
}
