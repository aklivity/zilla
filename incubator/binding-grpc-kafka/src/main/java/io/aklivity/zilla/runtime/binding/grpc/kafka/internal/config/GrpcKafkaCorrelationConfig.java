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

public final class GrpcKafkaCorrelationConfig
{
    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
        new KafkaOffsetFW.Builder()
            .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
            .partitionId(-1)
            .partitionOffset(0L)
            .stableOffset(KafkaOffsetType.HISTORICAL.value())
            .latestOffset(KafkaOffsetType.HISTORICAL.value())
            .build();

    private static final KafkaOffsetFW KAFKA_OFFSET_LIVE =
        new KafkaOffsetFW.Builder()
            .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
            .partitionId(-1)
            .partitionOffset(0L)
            .stableOffset(KafkaOffsetType.LIVE.value())
            .latestOffset(KafkaOffsetType.LIVE.value())
            .build();

    public final String16FW replyTo;

    public List<GrpcKafkaWithFetchFilterResult> filters;

    public GrpcKafkaCorrelationConfig(
        String16FW replyTo,
        List<GrpcKafkaWithFetchFilterResult> filters)
    {
        this.replyTo = replyTo;
        this.filters = filters;
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
