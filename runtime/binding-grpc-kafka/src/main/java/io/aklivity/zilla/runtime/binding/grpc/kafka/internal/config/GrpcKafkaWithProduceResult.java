/*
 * Copyright 2021-2023 Aklivity Inc
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

import io.aklivity.zilla.runtime.binding.grpc.kafka.config.GrpcKafkaCorrelationConfig;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckModeFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetCommittedFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;

public class GrpcKafkaWithProduceResult
{
    private static final KafkaOffsetCommittedFW KAFKA_OFFSET_HISTORICAL =
        new KafkaOffsetCommittedFW.Builder()
            .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
            .partitionId(-1)
            .partitionOffset(KafkaOffsetType.HISTORICAL.value())
            .build();

    private final List<GrpcKafkaWithProduceOverrideResult> overrides;
    private final GrpcKafkaCorrelationConfig correlation;
    private final String16FW topic;
    private final String16FW replyTo;
    private final KafkaAckMode acks;
    private final Supplier<DirectBuffer> keyRef;
    private final GrpcKafkaWithProduceHash hash;
    private final String16FW service;
    private final String16FW method;

    GrpcKafkaWithProduceResult(
        String16FW service,
        String16FW method,
        String16FW topic,
        KafkaAckMode acks,
        Supplier<DirectBuffer> keyRef,
        List<GrpcKafkaWithProduceOverrideResult> overrides,
        String16FW replyTo,
        GrpcKafkaCorrelationConfig correlation,
        GrpcKafkaWithProduceHash hash)
    {
        this.service = service;
        this.method = method;
        this.overrides = overrides;
        this.replyTo = replyTo;
        this.correlation = correlation;
        this.topic = topic;
        this.acks = acks;
        this.keyRef = keyRef;
        this.hash = hash;

        hash.updateHash(correlation.service.value());
        hash.updateHash(service.value());
        hash.updateHash(correlation.method.value());
        hash.updateHash(method.value());
        hash.updateHash(correlation.replyTo.value());
        hash.updateHash(replyTo.value());

        if (overrides != null)
        {
            overrides.forEach(o -> hash.updateHash(o.valueRef.get()));
        }

        hash.digestHash();
    }

    public String16FW topic()
    {
        return topic;
    }
    public String16FW replyTo()
    {
        return replyTo;
    }

    public void partitions(
        Array32FW.Builder<KafkaOffsetCommittedFW.Builder, KafkaOffsetCommittedFW> builder)
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
        else
        {
            final OctetsFW correlationId = hash.correlationId();
            builder
                .length(correlationId.sizeof())
                .value(correlationId.value(), 0, correlationId.sizeof());
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

        builder.item(this::service);
        builder.item(this::method);
        builder.item(this::replyTo);
        builder.item(this::correlationId);
    }

    private void service(
        KafkaHeaderFW.Builder builder)
    {
        final String16FW name = correlation.service;
        final DirectBuffer value = service.value();

        builder
            .nameLen(name.length())
            .name(name.value(), 0, name.length())
            .valueLen(service.length())
            .value(value, 0, service.length());
    }

    private void method(
        KafkaHeaderFW.Builder builder)
    {
        final String16FW name = correlation.method;
        DirectBuffer value = method.value();

        builder
            .nameLen(name.length())
            .name(name.value(), 0, name.length())
            .valueLen(method.length())
            .value(value, 0, method.length());
    }

    private void replyTo(
        KafkaHeaderFW.Builder builder)
    {
        builder
            .nameLen(correlation.replyTo.length())
            .name(correlation.replyTo.value(), 0, correlation.replyTo.length())
            .valueLen(replyTo.length())
            .value(replyTo.value(), 0, replyTo.length());
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

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        final OctetsFW hashCorrelationId = hash.correlationId();

        builder.item(i -> i
            .conditionsItem(c -> c
                .header(h -> h
                    .nameLen(correlation.correlationId.length())
                    .name(correlation.correlationId.value(), 0, correlation.correlationId.length())
                    .valueLen(hashCorrelationId.sizeof())
                    .value(hashCorrelationId.value(), 0, hashCorrelationId.sizeof()))));
    }
}
