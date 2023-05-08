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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal.config;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaAckModeFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.grpc.internal.types.String8FW;

public class KafkaGrpcConditionResult
{
    private static final String8FW HEADER_NAME_STATUS = new String8FW("zilla:status");

    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
        new KafkaOffsetFW.Builder()
            .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
            .partitionId(-1)
            .partitionOffset(KafkaOffsetType.HISTORICAL.value())
            .build();

    private final String16FW scheme;
    private final String16FW authority;
    private final String16FW topic;
    private final KafkaAckMode acks;
    private final List<KafkaGrpcFetchFilterResult> filters;
    private final String16FW replyTo;
    private final KafkaGrpcCorrelationConfig correlation;

    KafkaGrpcConditionResult(
        String16FW scheme,
        String16FW authority,
        String16FW topic,
        KafkaAckMode acks,
        List<KafkaGrpcFetchFilterResult> filters,
        String16FW replyTo,
        KafkaGrpcCorrelationConfig correlation)
    {
        this.scheme = scheme;
        this.authority = authority;
        this.topic = topic;
        this.acks = acks;
        this.filters = filters;
        this.replyTo = replyTo;
        this.correlation = correlation;
    }

    public String16FW scheme()
    {
        return scheme;
    }
    public String16FW authority()
    {
        return authority;
    }
    public String16FW topic()
    {
        return topic;
    }

    public void partitions(
        Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> builder)
    {
        builder.item(p -> p.set(KAFKA_OFFSET_HISTORICAL));
    }

    public void acks(
        KafkaAckModeFW.Builder builder)
    {
        builder.set(acks);
    }

    public void key(
        OctetsFW key,
        KafkaKeyFW.Builder builder)
    {
        builder.length(key.sizeof()).value(key);
    }

    public void headers(
        OctetsFW correlationId,
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        builder.item(i -> i.nameLen(correlation.correlationId.length())
            .name(correlation.correlationId.value(), 0, correlation.correlationId.length())
            .valueLen(correlationId.sizeof())
            .value(correlationId.buffer(), correlationId.offset(), correlationId.sizeof()));
    }

    public void headersWithStatusCode(
        OctetsFW correlationId,
        String16FW status,
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        headers(correlationId, builder);

        builder.item(i -> i.nameLen(HEADER_NAME_STATUS.length())
            .name(HEADER_NAME_STATUS.value(), 0, HEADER_NAME_STATUS.length())
            .valueLen(status.length())
            .value(status.value(), 0, status.length()));
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }
    }

    public String16FW replyTo()
    {
        return replyTo;
    }
}
