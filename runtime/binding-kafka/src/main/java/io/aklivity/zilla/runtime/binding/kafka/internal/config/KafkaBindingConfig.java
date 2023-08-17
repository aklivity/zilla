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
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.ValidatorFactory;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.StringValidatorConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.ValidatorConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

public final class KafkaBindingConfig
{
    public final long id;
    public final String name;
    public final KafkaOptionsConfig options;
    public final KindConfig kind;
    public final List<KafkaRouteConfig> routes;
    private final ValidatorFactory validator = ValidatorFactory.instantiate();
    public final Map<String, KafkaTopicValidators> validatorsMap;

    public KafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = KafkaOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(KafkaRouteConfig::new).collect(toList());
        this.validatorsMap = options != null &&
                options.topics != null
                    ? options.topics.stream()
                    .collect(Collectors.toMap(t -> t.name, t -> new KafkaTopicValidators(
                        t.key != null ? validator.create(t.key.type, supplyValidatorConfig(t.key.type, t.key)) : null,
                        t.value != null ? validator.create(t.value.type, supplyValidatorConfig(t.value.type, t.value)) : null
                        ))) : null;
    }

    public KafkaRouteConfig resolve(
        long authorization,
        String topic)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(topic))
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
        return topic != null &&
                options != null &&
                options.topics != null
                    ? options.topics.stream().filter(t -> topic.equals(t.name)).findFirst().orElse(null).deltaType
                    : deltaType;
    }

    public KafkaOffsetType supplyDefaultOffset(
        String topic)
    {
        return topic != null &&
                options != null &&
                options.topics != null
                    ? options.topics.stream().filter(t -> topic.equals(t.name)).findFirst().orElse(null).defaultOffset
                    : HISTORICAL;
    }

    private ValidatorConfig supplyValidatorConfig(
        String type,
        KafkaTopicKeyValueConfig config)
    {
        if ("string".equals(type))
        {
            return new StringValidatorConfig(config.encoding);
        }

        return null;
    }
}
