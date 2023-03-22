/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;

import java.util.List;
import java.util.Optional;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.engine.config.WithConfig;


public final class GrpcKafkaWithConfig extends WithConfig
{
    public final String topic;
    public final KafkaAckMode acks;
    public final Optional<String> key;
    public final Optional<List<GrpcKafkaWithProduceOverrideConfig>> overrides;
    public final String replyTo;
    public final Optional<List<GrpcKafkaWithFetchFilterConfig>> filters;


    public GrpcKafkaWithConfig(
        String topic,
        KafkaAckMode acks,
        String key,
        List<GrpcKafkaWithProduceOverrideConfig> overrides,
        String replyTo,
        List<GrpcKafkaWithFetchFilterConfig> filters)
    {
        this.topic = topic;
        this.acks = acks;
        this.overrides = Optional.ofNullable(overrides);
        this.key = Optional.ofNullable(key);
        this.replyTo = replyTo;
        this.filters = Optional.ofNullable(filters);
    }
}
