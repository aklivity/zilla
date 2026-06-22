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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicTransformsConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;

public class KafkaTopicType
{
    private static final String TRANSFORM_PATH = "^\\$\\{message\\.(key|value)\\.([A-Za-z_][A-Za-z0-9_]*)\\}$";
    private static final Pattern TRANSFORM_PATH_PATTERN = Pattern.compile(TRANSFORM_PATH);
    private static final String TRANSFORM_INTERNAL_PATH = "$.%s";

    public static final KafkaTopicType DEFAULT_TOPIC_TYPE = new KafkaTopicType();

    public final ModelHandler keyModel;
    public final ModelHandler valueModel;
    public final KafkaTopicTransformsType transforms;

    private final Matcher topicMatch;
    private final Matcher matcher;

    private KafkaTopicType()
    {
        this.topicMatch = null;
        this.keyModel = null;
        this.valueModel = null;
        this.transforms = null;
        this.matcher = TRANSFORM_PATH_PATTERN.matcher("");
    }

    public KafkaTopicType(
        EngineContext context,
        KafkaTopicConfig topicConfig)
    {
        this.matcher = TRANSFORM_PATH_PATTERN.matcher("");
        this.topicMatch = topicConfig.name != null ? asMatcher(topicConfig.name) : null;
        this.transforms = topicConfig.transforms != null ? transforms(topicConfig.transforms) : null;
        this.keyModel = topicConfig.key != null ? key(context.supplyModel(topicConfig.key)) : null;
        this.valueModel = topicConfig.value != null ? headers(context.supplyModel(topicConfig.value)) : null;
    }

    public boolean matches(
        String topic)
    {
        return this.topicMatch == null || this.topicMatch.reset(topic).matches();
    }

    private ModelHandler key(
        ModelHandler handler)
    {
        if (handler != null && transforms != null && transforms.extractKey != null)
        {
            handler.extract(transforms.extractKey);
        }
        return handler;
    }

    private ModelHandler headers(
        ModelHandler handler)
    {
        if (handler != null && transforms != null && transforms.extractHeaders != null)
        {
            for (KafkaTopicHeaderType header : transforms.extractHeaders)
            {
                handler.extract(header.path);
            }
        }
        return handler;
    }


    private KafkaTopicTransformsType transforms(
        KafkaTopicTransformsConfig transforms)
    {
        String transformKey = Optional.ofNullable(transforms.extractKey)
            .filter(key -> matcher.reset(key).matches())
            .map(key -> String.format(TRANSFORM_INTERNAL_PATH, matcher.group(2)))
            .orElse(transforms.extractKey);

        List<KafkaTopicHeaderType> transformHeaders = Optional.ofNullable(transforms.extractHeaders)
            .orElse(emptyList())
            .stream()
            .filter(header -> matcher.reset(header.path).matches())
            .map(header -> new KafkaTopicHeaderType(header.name,
                String.format(TRANSFORM_INTERNAL_PATH, matcher.group(2))))
            .toList();

        return new KafkaTopicTransformsType(transformKey, transformHeaders);
    }

    private static Matcher asMatcher(
        String topic)
    {
        return Pattern.compile(topic.replaceAll("\\{[^}]+\\}", ".+")).matcher("");
    }
}
