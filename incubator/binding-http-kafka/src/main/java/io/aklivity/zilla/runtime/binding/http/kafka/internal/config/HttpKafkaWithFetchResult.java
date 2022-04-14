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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public class HttpKafkaWithFetchResult
{
    private final KafkaOffsetFW historical =
            new KafkaOffsetFW.Builder()
                .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
                .partitionId(-1)
                .partitionOffset(KafkaOffsetType.HISTORICAL.value())
                .build();

    private final String16FW topic;
    private final Array32FW<KafkaOffsetFW> partitions;
    private final List<HttpKafkaWithFetchFilterResult> filters;
    private final String etag;
    private final long timeout;

    HttpKafkaWithFetchResult(
        String16FW topic,
        Array32FW<KafkaOffsetFW> partitions,
        List<HttpKafkaWithFetchFilterResult> filters,
        String etag,
        long timeout)
    {
        this.topic = topic;
        this.partitions = partitions;
        this.filters = filters;
        this.etag = etag;
        this.timeout = timeout;
    }

    public String16FW topic()
    {
        return topic;
    }

    public void partitions(
        Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> builder)
    {
        if (partitions != null)
        {
            // TODO: fieldCount incorrect if using builder.set(partitions) instead (generator)
            partitions.forEach(p -> builder.item(i -> i.set(p)));
        }

        builder.item(p -> p.set(historical));
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }
    }

    public String etag()
    {
        return etag;
    }

    public long timeout()
    {
        return timeout;
    }
}
