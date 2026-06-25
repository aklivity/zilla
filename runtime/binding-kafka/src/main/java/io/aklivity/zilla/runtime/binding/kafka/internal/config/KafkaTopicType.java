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
import static java.util.Collections.emptySet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    public final Set<String> keyExtractPaths;
    public final Set<String> valueExtractPaths;

    private final Matcher topicMatch;
    private final Matcher matcher;

    private KafkaTopicType()
    {
        this.topicMatch = null;
        this.keyModel = null;
        this.valueModel = null;
        this.transforms = null;
        this.keyExtractPaths = emptySet();
        this.valueExtractPaths = emptySet();
        this.matcher = TRANSFORM_PATH_PATTERN.matcher("");
    }

    public KafkaTopicType(
        EngineContext context,
        KafkaTopicConfig topicConfig)
    {
        this.matcher = TRANSFORM_PATH_PATTERN.matcher("");
        this.topicMatch = topicConfig.name != null ? asMatcher(topicConfig.name) : null;
        this.transforms = topicConfig.transforms != null ? transforms(topicConfig.transforms) : null;
        this.keyModel = topicConfig.key != null ? context.supplyModel(topicConfig.key) : null;
        this.valueModel = topicConfig.value != null ? context.supplyModel(topicConfig.value) : null;
        this.keyExtractPaths = keyPaths();
        this.valueExtractPaths = valuePaths();
    }

    public boolean matches(
        String topic)
    {
        return this.topicMatch == null || this.topicMatch.reset(topic).matches();
    }

    private Set<String> keyPaths()
    {
        Set<String> paths = new LinkedHashSet<>();
        if (transforms != null && transforms.extractKey != null)
        {
            paths.add(transforms.extractKey);
        }
        return paths;
    }

    private Set<String> valuePaths()
    {
        Set<String> paths = new LinkedHashSet<>();
        if (transforms != null && transforms.extractHeaders != null)
        {
            for (KafkaTopicHeaderType header : transforms.extractHeaders)
            {
                paths.add(header.path);
            }
        }
        return paths;
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
