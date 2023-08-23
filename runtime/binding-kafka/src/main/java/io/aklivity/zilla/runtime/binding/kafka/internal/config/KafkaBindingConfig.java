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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.AvroValidator;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.ValidatorFactory;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
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
    private final ValidatorFactory validator = ValidatorFactory.instantiate();
    public final Map<String, KafkaTopicType> validatorsMap;
    private Map<String, CatalogHandler> handlerMap;

    public KafkaBindingConfig(
        BindingConfig binding)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = KafkaOptionsConfig.class.cast(binding.options);
        this.routes = binding.routes.stream().map(KafkaRouteConfig::new).collect(toList());
        this.resolveId = binding.resolveId;
        this.handlerMap = new HashMap<>();
        this.validatorsMap = options != null &&
                options.topics != null
                    ? options.topics.stream()
                    .collect(Collectors.toMap(t -> t.name, t -> new KafkaTopicType(
                    t.key != null ? validator.create(t.key) : null,
                    t.value != null ? validator.create(t.value) : null
                    ))) : null;
    }

    public void init(
        LongFunction<CatalogHandler> catalog,
        ToLongFunction<String> resolveId)
    {
        if (validatorsMap != null)
        {
            for (KafkaTopicType topic : validatorsMap.values())
            {
                if (topic.key != null && topic.key instanceof AvroValidator)
                {
                    for (KafkaCatalogConfig config : ((AvroValidator) topic.key).catalogList)
                    {
                        if (config != null && !handlerMap.containsKey(config.name))
                        {
                            handlerMap.put(config.name, catalog.apply(resolveId.applyAsLong(config.name)));
                        }
                    }
                    ((AvroValidator) topic.key).handler.putAll(handlerMap);
                }

                if (topic.value != null && topic.value instanceof AvroValidator)
                {
                    for (KafkaCatalogConfig config : ((AvroValidator) topic.value).catalogList)
                    {
                        if (config != null && !handlerMap.containsKey(config.name))
                        {
                            handlerMap.put(config.name, catalog.apply(resolveId.applyAsLong(config.name)));
                        }
                    }
                    ((AvroValidator) topic.value).handler.putAll(handlerMap);
                }
            }
        }
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
        KafkaTopicConfig config = topic(topic);
        return config != null ? config.deltaType : deltaType;
    }

    public KafkaOffsetType supplyDefaultOffset(
        String topic)
    {
        KafkaTopicConfig config = topic(topic);
        return config != null ? config.defaultOffset : HISTORICAL;
    }
}
