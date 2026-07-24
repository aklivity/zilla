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

import java.util.function.Function;

public class KafkaGrpcCorrelationConfig
{
    public final String correlationId;
    public final String service;
    public final String method;
    public final String replyTo;

    public static KafkaGrpcCorrelationConfigBuilder<KafkaGrpcCorrelationConfig> builder()
    {
        return new KafkaGrpcCorrelationConfigBuilder<>(KafkaGrpcCorrelationConfig.class::cast);
    }

    public static <T> KafkaGrpcCorrelationConfigBuilder<T> builder(
        Function<KafkaGrpcCorrelationConfig, T> mapper)
    {
        return new KafkaGrpcCorrelationConfigBuilder<>(mapper);
    }

    KafkaGrpcCorrelationConfig(
        String correlationId,
        String service,
        String method,
        String replyTo)
    {
        this.correlationId = correlationId;
        this.service = service;
        this.method = method;
        this.replyTo = replyTo;
    }
}
