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
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckModeFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;

public class GrpcKafkaWithResult
{
    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
        new KafkaOffsetFW.Builder()
            .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
            .partitionId(-1)
            .partitionOffset(KafkaOffsetType.HISTORICAL.value())
            .build();

    private List<GrpcKafkaWithProduceOverrideResult> overrides;
    private GrpcKafkaCorrelationConfig correlation;
    private final String16FW topic;
    private KafkaAckMode acks;
    private Supplier<DirectBuffer> keyRef;
    private GrpcKafkaWithProduceHash hash;


    GrpcKafkaWithResult(
        String16FW topic,
        KafkaAckMode acks,
        Supplier<DirectBuffer> keyRef,
        List<GrpcKafkaWithProduceOverrideResult> overrides,
        GrpcKafkaWithProduceHash hash,
        GrpcKafkaCorrelationConfig correlation)
    {
        this.overrides = overrides;
        this.correlation = correlation;
        this.topic = topic;
        this.acks = acks;
        this.keyRef = keyRef;
        this.hash = hash;
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
        KafkaKeyFW.Builder builder)
    {
        final DirectBuffer key = keyRef.get();
        if (key != null)
        {
            builder
                .length(key.capacity())
                .value(key, 0, key.capacity());
        }
    }

    public void headers(
        Array32FW.Builder<KafkaHeaderFW.Builder,
            KafkaHeaderFW> builder)
    {
        if (overrides != null)
        {
            overrides.forEach(o -> builder.item(o::header));
        }

        builder.item(this::correlationId);
    }

    public GrpcKafkaCorrelationConfig correlation()
    {
        return correlation;
    }

    private void correlationId(
        KafkaHeaderFW.Builder builder)
    {
        final OctetsFW correlationId = hash.correlationId();

        builder
            .nameLen(correlation.correlationId.length())
            .name(correlation.correlationId.value(), 0, correlation.correlationId.length())
            .valueLen(correlationId.sizeof())
            .value(correlationId.value(), 0, correlationId.sizeof());
    }
}
