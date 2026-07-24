/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.binding.kafka.grpc;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConditionConfig;

public final class KafkaGrpcConditionConfig extends ConditionConfig
{
    public final String topic;
    public final Optional<String> key;
    public final Optional<Map<String, String>> headers;
    public final Optional<String> replyTo;
    public final Optional<String> service;
    public final Optional<String> method;

    public static KafkaGrpcConditionConfigBuilder<KafkaGrpcConditionConfig> builder()
    {
        return new KafkaGrpcConditionConfigBuilder<>(KafkaGrpcConditionConfig.class::cast);
    }

    public static <T> KafkaGrpcConditionConfigBuilder<T> builder(
        Function<ConditionConfig, T> mapper)
    {
        return new KafkaGrpcConditionConfigBuilder<>(mapper);
    }

    KafkaGrpcConditionConfig(
        String topic,
        String replyTo,
        String key,
        Map<String, String> headers,
        String service,
        String method)
    {
        this.topic = topic;
        this.key =  Optional.ofNullable(key);
        this.headers = Optional.ofNullable(headers);
        this.replyTo = Optional.ofNullable(replyTo);
        this.service =  Optional.ofNullable(service);
        this.method =  Optional.ofNullable(method);
    }
}
