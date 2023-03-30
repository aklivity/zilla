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

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;

public final class GrpcKafkaCorrelationConfig
{
    public final String16FW replyTo;
    public final String16FW correlationId;
    public final GrpcKafkaWithProduceHash hash;
    public List<GrpcKafkaWithFetchFilterResult> filters;

    public GrpcKafkaCorrelationConfig(
        String16FW replyTo,
        String16FW correlationId,
        GrpcKafkaWithProduceHash hash,
        List<GrpcKafkaWithFetchFilterResult> filters)
    {
        this.replyTo = replyTo;
        this.correlationId = correlationId;
        this.hash = hash;
        this.filters = filters;
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }

        final OctetsFW hashCorrelationId = hash.correlationId();

        if (correlationId != null)
        {
            builder.item(i -> i
                .conditionsItem(c -> c
                    .header(h -> h
                        .nameLen(correlationId.length())
                        .name(correlationId.value(), 0, correlationId.length())
                        .valueLen(hashCorrelationId.sizeof())
                        .value(hashCorrelationId.value(), 0, hashCorrelationId.sizeof()))));
        }
    }
}
