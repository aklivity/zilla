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

import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.specs.engine.internal.types.Varuint32FW;

public class GrpcKafkaWithFetchResult
{
    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
        new KafkaOffsetFW.Builder()
            .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
            .partitionId(-1)
            .partitionOffset(KafkaOffsetType.HISTORICAL.value())
            .build();

    private final Array32FW<KafkaOffsetFW> partitions;
    private final List<GrpcKafkaWithFetchFilterResult> filters;
    private final String16FW topic;
    private final Varuint32FW lastMessageId;

    GrpcKafkaWithFetchResult(
        String16FW topic,
        Array32FW<KafkaOffsetFW> partitions,
        List<GrpcKafkaWithFetchFilterResult> filters,
        Varuint32FW lastMessageId)
    {
        this.partitions = partitions;
        this.filters = filters;
        this.topic = topic;
        this.lastMessageId = lastMessageId;
    }

    public String16FW topic()
    {
        return topic;
    }

    public Varuint32FW lastMessageId()
    {
        return lastMessageId;
    }

    public void partitions(
        Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> builder)
    {
        if (partitions != null)
        {
            partitions.forEach(p -> builder.item(n -> n.set(p)));
        }
        else
        {
            builder.item(p -> p.set(KAFKA_OFFSET_HISTORICAL));
        }
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }
    }
}
