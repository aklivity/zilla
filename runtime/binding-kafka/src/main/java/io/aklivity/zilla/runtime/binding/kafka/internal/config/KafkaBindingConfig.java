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

import static io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicType.DEFAULT_TOPIC_TYPE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.HISTORICAL;
import static java.util.stream.Collectors.toList;

import java.util.Collections;
import java.util.List;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaServerConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class KafkaBindingConfig
{
    public final long id;
    public final String name;
    public final KafkaOptionsConfig options;
    public final KindConfig kind;
    public final List<KafkaRouteConfig> routes;
    public final ToLongFunction<String> resolveId;
    public final List<KafkaTopicType> topicTypes;

    public KafkaBindingConfig(
        BindingConfig binding,
        EngineContext context)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = KafkaOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(KafkaRouteConfig::new).collect(toList());
        this.resolveId = binding.resolveId;
        this.topicTypes = options != null && options.topics != null
            ? options.topics.stream().map(t -> new KafkaTopicType(context, t)).collect(toList()) : Collections.emptyList();
    }

    public KafkaRouteConfig resolve(
        long authorization,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(topic, null))
            .findFirst()
            .orElse(null);
    }

    public KafkaRouteConfig resolve(
        long authorization,
        String topic,
        String groupId)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(topic, groupId))
            .findFirst()
            .orElse(null);
    }

    public KafkaTopicConfig topic(
        String topic)
    {
        return topic != null &&
                options != null &&
                options.topics != null
                    ? options.topics.stream().filter(t -> topic.equals(t.name)).findFirst().orElse(null)
                    : null;
    }

    public KafkaSaslConfig sasl()
    {
        return options != null ? options.sasl : null;
    }

    public List<KafkaServerConfig> servers()
    {
        return options != null ? options.servers : null;
    }

    public KafkaDeltaType supplyDeltaType(
        String topic,
        KafkaDeltaType deltaType)
    {
        KafkaTopicConfig config = topic(topic);
        return config != null && config.deltaType != null ? config.deltaType : deltaType;
    }

    public KafkaOffsetType supplyDefaultOffset(
        String topic)
    {
        KafkaTopicConfig config = topic(topic);
        return config != null && config.defaultOffset != null ? config.defaultOffset : HISTORICAL;
    }

    public KafkaTopicType resolveTopicType(
        String topic)
    {
        KafkaTopicType matchedType = DEFAULT_TOPIC_TYPE;
        for (KafkaTopicType topicType : topicTypes)
        {
            if (topicType.matches(topic))
            {
                matchedType = topicType;
                break;
            }
        }
        return matchedType;
    }
}
