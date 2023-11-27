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

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.HISTORICAL;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaSaslConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.validator.FragmentValidator;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;

public final class KafkaBindingConfig
{
    public final long id;
    public final String name;
    public final KafkaOptionsConfig options;
    public final KindConfig kind;
    public final List<KafkaRouteConfig> routes;
    public final ToLongFunction<String> resolveId;
    public final Map<String, FragmentValidator> fragmentReaders;
    public final Map<String, FragmentValidator> fragmentWriters;
    public final Map<String, ValueValidator> valueReaders;
    public final Map<String, ValueValidator> valueWriters;

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
        this.valueReaders = options != null && options.topics != null
                ? options.topics.stream()
                .collect(Collectors.toMap(
                    t -> t.name,
                    t -> t.key != null
                        ? context.createValueReader(t.key)
                        : ValueValidator.NONE))
                : null;
        this.valueWriters = options != null && options.topics != null
                ? options.topics.stream()
                .collect(Collectors.toMap(
                    t -> t.name,
                    t -> t.key != null
                        ? context.createValueWriter(t.key)
                        : ValueValidator.NONE))
                : null;
        this.fragmentReaders = options != null && options.topics != null
                ? options.topics.stream()
                .collect(Collectors.toMap(
                    t -> t.name,
                    t -> t.value != null
                        ? context.createFragmentReader(t.value)
                        : FragmentValidator.NONE))
                : null;
        this.fragmentWriters = options != null && options.topics != null
                ? options.topics.stream()
                .collect(Collectors.toMap(
                    t -> t.name,
                    t -> t.value != null
                        ? context.createFragmentWriter(t.value)
                        : FragmentValidator.NONE))
                : null;
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

    public boolean bootstrap(
        String topic)
    {
        return topic != null &&
                options != null &&
                options.bootstrap != null &&
                options.bootstrap.contains(topic);
    }

    public KafkaSaslConfig sasl()
    {
        return options != null ? options.sasl : null;
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

    public ValueValidator resolveValueReader(
        String topic)
    {
        return valueReaders != null ? valueReaders.getOrDefault(topic, ValueValidator.NONE) : ValueValidator.NONE;
    }

    public ValueValidator resolveValueWriter(
        String topic)
    {
        return valueWriters != null ? valueWriters.getOrDefault(topic, ValueValidator.NONE) : ValueValidator.NONE;
    }

    public FragmentValidator resolveFragmentReader(
        String topic)
    {
        return fragmentReaders != null ? fragmentReaders.getOrDefault(topic, FragmentValidator.NONE) : FragmentValidator.NONE;
    }

    public FragmentValidator resolveFragmentWriter(
        String topic)
    {
        return fragmentWriters != null ? fragmentWriters.getOrDefault(topic, FragmentValidator.NONE) : FragmentValidator.NONE;
    }
}
