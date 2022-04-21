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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;

public class HttpKafkaWithFetchResult
{
    private static final String8FW HTTP_HEADER_NAME_STATUS = new String8FW(":status");
    private static final String8FW HTTP_HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");

    private static final String16FW HTTP_HEADER_VALUE_STATUS_200 = new String16FW("200");

    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
            new KafkaOffsetFW.Builder()
                .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
                .partitionId(-1)
                .partitionOffset(KafkaOffsetType.HISTORICAL.value())
                .build();

    private final String16FW topic;
    private final Array32FW<KafkaOffsetFW> partitions;
    private final List<HttpKafkaWithFetchFilterResult> filters;
    private final String16FW etag;
    private final long timeout;
    private final HttpKafkaWithFetchMergeResult merge;

    HttpKafkaWithFetchResult(
        String16FW topic,
        Array32FW<KafkaOffsetFW> partitions,
        List<HttpKafkaWithFetchFilterResult> filters,
        String16FW etag,
        long timeout,
        HttpKafkaWithFetchMergeResult merge)
    {
        this.topic = topic;
        this.partitions = partitions;
        this.filters = filters;
        this.etag = etag;
        this.timeout = timeout;
        this.merge = merge;
    }

    public String16FW topic()
    {
        return topic;
    }

    public void partitions(
        Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> builder)
    {
        if (merge == null && partitions != null)
        {
            // TODO: fieldCount incorrect if using builder.set(partitions) instead (generator)
            partitions.forEach(p -> builder.item(i -> i.set(p)));
        }

        builder.item(p -> p.set(KAFKA_OFFSET_HISTORICAL));
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        if (filters != null)
        {
            filters.forEach(f -> builder.item(f::filter));
        }
    }

    public String16FW etag()
    {
        return etag;
    }

    public boolean conditional()
    {
        return etag != null;
    }

    public long timeout()
    {
        return timeout;
    }

    public boolean merge()
    {
        return merge != null;
    }

    public void headers(
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> builder)
    {
        final DirectBuffer contentType = merge.contentType.value();

        builder.item(i -> i
                .name(HTTP_HEADER_NAME_STATUS)
                .value(HTTP_HEADER_VALUE_STATUS_200));

        builder.item(i -> i
            .name(HTTP_HEADER_NAME_CONTENT_TYPE)
            .value(contentType, 0, contentType.capacity()));
    }

    public boolean partitions(
        Array32FW<KafkaOffsetFW> partitions)
    {
        return this.partitions != null && this.partitions.equals(partitions);
    }

    public String16FW contentType()
    {
        return merge.contentType;
    }

    public OctetsFW header()
    {
        return merge.header;
    }

    public OctetsFW separator()
    {
        return merge.separator;
    }

    public OctetsFW trailer()
    {
        return merge.trailer;
    }

    public int padding()
    {
        return Math.max(merge.header.sizeof(), merge.separator.sizeof()) + merge.trailer.sizeof();
    }
}
