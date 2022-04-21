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
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;

public class HttpKafkaWithProduceResult
{
    private static final HttpKafkaWithProduceAsyncHeaderResult HTTP_STATUS_202 =
        new HttpKafkaWithProduceAsyncHeaderResult(new String8FW(":status"), new String16FW("202"));
    private static final String8FW HTTP_HEADER_NAME_CONTENT_LENGTH = new String8FW("content-length");
    private static final String8FW HTTP_HEADER_NAME_PREFER = new String8FW("prefer");
    private static final String8FW HTTP_HEADER_NAME_IF_MATCH = new String8FW("if-match");

    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
            new KafkaOffsetFW.Builder()
                .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
                .partitionId(-1)
                .partitionOffset(KafkaOffsetType.HISTORICAL.value())
                .build();

    private final HttpKafkaCorrelationConfig correlation;
    private final String16FW topic;
    private final DirectBuffer key;
    private final List<HttpKafkaWithProduceOverrideResult> overrides;
    private final String16FW ifMatch;
    private final String16FW replyTo;
    private final List<HttpKafkaWithProduceAsyncHeaderResult> async;
    private final String16FW correlationId;
    private final String16FW idempotencyKey;
    private final long timeout;

    HttpKafkaWithProduceResult(
        HttpKafkaCorrelationConfig correlation,
        String16FW topic,
        DirectBuffer key,
        List<HttpKafkaWithProduceOverrideResult> overrides,
        String16FW ifMatch,
        String16FW replyTo,
        String16FW idempotencyKey,
        List<HttpKafkaWithProduceAsyncHeaderResult> async,
        String16FW correlationId,
        long timeout)
    {
        this.correlation = correlation;
        this.topic = topic;
        this.key = key;
        this.overrides = overrides;
        this.ifMatch = ifMatch;
        this.replyTo = replyTo;
        this.idempotencyKey = idempotencyKey;
        this.async = async;
        this.correlationId = correlationId;
        this.timeout = timeout;
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

    public String16FW replyTo()
    {
        return replyTo;
    }

    public void key(
        KafkaKeyFW.Builder builder)
    {
        if (key != null)
        {
            builder
                .length(key.capacity())
                .value(key, 0, key.capacity());
        }
    }

    public void headers(
        Array32FW<HttpHeaderFW> headers,
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        headers.forEach(h -> header(h, builder));
        builder.item(this::replyTo);
        builder.item(this::correlationId);

        if (overrides != null)
        {
            overrides.forEach(o -> builder.item(o::header));
        }
    }

    private void header(
        HttpHeaderFW header,
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        final String8FW name = header.name();

        if (!HTTP_HEADER_NAME_CONTENT_LENGTH.equals(name) &&
            !HTTP_HEADER_NAME_PREFER.equals(name))
        {
            final String16FW value = HTTP_HEADER_NAME_IF_MATCH.equals(name) ? ifMatch : header.value();

            builder.item(i -> i
                .nameLen(name.length())
                .name(name.value(), 0, name.length())
                .valueLen(value.length())
                .value(value.value(), 0, value.length()));
        }
    }

    private void replyTo(
        KafkaHeaderFW.Builder builder)
    {
        if (replyTo != null)
        {
            builder
                .nameLen(correlation.replyTo.length())
                .name(correlation.replyTo.value(), 0, correlation.replyTo.length())
                .valueLen(replyTo.length())
                .value(replyTo.value(), 0, replyTo.length());
        }
    }

    private void correlationId(
        KafkaHeaderFW.Builder builder)
    {
        if (idempotencyKey != null)
        {
            builder
                .nameLen(correlation.correlationId.length())
                .name(correlation.correlationId.value(), 0, correlation.correlationId.length())
                .valueLen(idempotencyKey.length())
                .value(idempotencyKey.value(), 0, idempotencyKey.length());
        }
    }

    public String16FW idempotencyKey()
    {
        return idempotencyKey;
    }

    public void correlated(
        Array32FW<KafkaHeaderFW> headers,
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> builder)
    {
        headers.forEach(h -> correlated(h, builder));
    }

    private void correlated(
        KafkaHeaderFW header,
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> builder)
    {
        final DirectBuffer name = header.name().value();
        final DirectBuffer value = header.value().value();

        if (!correlation.correlationId.value().equals(name))
        {
            builder.item(i -> i
                .name(name, 0, name.capacity())
                .value(value, 0, value.capacity()));
        }
    }

    public void async(
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> builder)
    {
        if (async != null)
        {
            builder.item(HTTP_STATUS_202::header);
            async.forEach(a -> builder.item(a::header));
        }
    }

    public boolean async()
    {
        return async != null;
    }

    public boolean correlated()
    {
        return correlationId != null;
    }

    public long timeout()
    {
        return timeout;
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        final String16FW correlationId = this.correlationId != null ? this.correlationId : this.idempotencyKey;

        if (correlationId != null)
        {
            builder.item(i -> i
                .conditionsItem(c -> c
                    .header(h -> h
                        .nameLen(correlation.correlationId.length())
                        .name(correlation.correlationId.value(), 0, correlation.correlationId.length())
                        .valueLen(correlationId.length())
                        .value(correlationId.value(), 0, correlationId.length()))));
        }
    }
}
