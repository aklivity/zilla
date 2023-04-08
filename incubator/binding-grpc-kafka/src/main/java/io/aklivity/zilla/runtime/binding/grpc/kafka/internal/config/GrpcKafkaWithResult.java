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
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaFilterFW;
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

    private final List<GrpcKafkaWithProduceOverrideResult> overrides;
    private final List<GrpcKafkaWithFetchFilterResult> filters;
    private final GrpcKafkaCorrelationConfig correlation;
    private final String16FW topic;
    private final String16FW replyTo;
    private final KafkaAckMode acks;
    private final Supplier<DirectBuffer> keyRef;
    private final GrpcKafkaWithProduceHash hash;
    private final String16FW service;
    private final String16FW method;
    private final String16FW request;
    private final String16FW response;

    GrpcKafkaWithResult(
        String16FW service,
        String16FW method,
        String16FW request,
        String16FW response,
        String16FW topic,
        KafkaAckMode acks,
        Supplier<DirectBuffer> keyRef,
        List<GrpcKafkaWithProduceOverrideResult> overrides,
        String16FW replyTo,
        List<GrpcKafkaWithFetchFilterResult> filters,
        GrpcKafkaCorrelationConfig correlation,
        GrpcKafkaWithProduceHash hash)
    {
        this.method = method;
        this.service = service;
        this.request = request;
        this.response = response;
        this.overrides = overrides;
        this.replyTo = replyTo;
        this.filters = filters;
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
    public String16FW replyTo()
    {
        return replyTo;
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

        builder.item(this::service);
        builder.item(this::method);
        builder.item(this::request);
        builder.item(this::response);
        builder.item(this::correlationId);
    }

    private void service(
        KafkaHeaderFW.Builder builder)
    {
        builder
            .nameLen(correlation.service.length())
            .name(correlation.service.value(), 0, correlation.service.length())
            .valueLen(service.length())
            .value(service.value(), 0, service.length());
    }

    private void method(
        KafkaHeaderFW.Builder builder)
    {
        builder
            .nameLen(correlation.method.length())
            .name(correlation.method.value(), 0, correlation.method.length())
            .valueLen(method.length())
            .value(method.value(), 0, method.length());
    }

    private void request(
        KafkaHeaderFW.Builder builder)
    {
        builder
            .nameLen(correlation.request.length())
            .name(correlation.request.value(), 0, correlation.request.length())
            .valueLen(request.length())
            .value(request.value(), 0, request.length());
    }


    private void response(
        KafkaHeaderFW.Builder builder)
    {
        builder
            .nameLen(correlation.response.length())
            .name(correlation.response.value(), 0, correlation.response.length())
            .valueLen(response.length())
            .value(response.value(), 0, response.length());
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
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }

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
