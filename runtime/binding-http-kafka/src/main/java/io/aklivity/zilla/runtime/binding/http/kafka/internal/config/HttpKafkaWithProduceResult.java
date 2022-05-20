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
import java.util.function.Supplier;

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
    private static final HttpKafkaWithProduceAsyncHeaderResult HTTP_CONTENT_LENGTH_0 =
            new HttpKafkaWithProduceAsyncHeaderResult(new String8FW("content-length"), new String16FW("0"));

    private static final String8FW HTTP_HEADER_NAME_CONTENT_LENGTH = new String8FW("content-length");
    private static final String8FW HTTP_HEADER_NAME_PREFER = new String8FW("prefer");
    private static final String8FW HTTP_HEADER_NAME_IF_MATCH = new String8FW("if-match");
    private static final String8FW HTTP_HEADER_NAME_STATUS = new String8FW(":status");

    private static final String16FW HTTP_HEADER_VALUE_204 = new String16FW("204");

    private static final KafkaOffsetFW KAFKA_OFFSET_HISTORICAL =
            new KafkaOffsetFW.Builder()
                .wrap(new UnsafeBuffer(new byte[32]), 0, 32)
                .partitionId(-1)
                .partitionOffset(KafkaOffsetType.HISTORICAL.value())
                .build();

    private final HttpKafkaCorrelationConfig correlation;
    private final String16FW topic;
    private final Supplier<DirectBuffer> keyRef;
    private final List<HttpKafkaWithProduceOverrideResult> overrides;
    private final String16FW ifMatch;
    private final String16FW replyTo;
    private final List<HttpKafkaWithProduceAsyncHeaderResult> async;
    private final HttpKafkaWithProduceHash hash;
    private final long timeout;
    private final boolean idempotent;

    HttpKafkaWithProduceResult(
        HttpKafkaCorrelationConfig correlation,
        String16FW topic,
        Supplier<DirectBuffer> keyRef,
        List<HttpKafkaWithProduceOverrideResult> overrides,
        String16FW ifMatch,
        String16FW replyTo,
        String16FW idempotencyKey,
        List<HttpKafkaWithProduceAsyncHeaderResult> async,
        HttpKafkaWithProduceHash hash,
        long timeout)
    {
        this.correlation = correlation;
        this.topic = topic;
        this.keyRef = keyRef;
        this.overrides = overrides;
        this.ifMatch = ifMatch;
        this.replyTo = replyTo;
        this.async = async;
        this.hash = hash;
        this.idempotent = idempotencyKey != null;
        this.timeout = timeout;
    }

    public void updateHash(
        DirectBuffer value)
    {
        hash.updateHash(value);
    }

    public void digestHash()
    {
        hash.digestHash();
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
        final DirectBuffer key = keyRef.get();
        if (key != null)
        {
            builder
                .length(key.capacity())
                .value(key, 0, key.capacity());

            hash.updateHash(key);
        }
    }

    public void headers(
        Array32FW<HttpHeaderFW> headers,
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        headers.forEach(h -> header(h, builder));

        if (replyTo != null)
        {
            builder.item(this::replyTo);
        }

        if (overrides != null)
        {
            overrides.forEach(o -> builder.item(o::header));
        }
    }

    public void trailers(
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        if (hash != null)
        {
            builder.item(this::correlationId);
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

            hash.updateHash(name.value());
            hash.updateHash(value.value());
        }
    }

    private void replyTo(
        KafkaHeaderFW.Builder builder)
    {
        builder
            .nameLen(correlation.replyTo.length())
            .name(correlation.replyTo.value(), 0, correlation.replyTo.length())
            .valueLen(replyTo.length())
            .value(replyTo.value(), 0, replyTo.length());

        hash.updateHash(correlation.replyTo.value());
        hash.updateHash(replyTo.value());
    }

    private void correlationId(
        KafkaHeaderFW.Builder builder)
    {
        final String16FW correlationId = hash.correlationId();

        builder
            .nameLen(correlation.correlationId.length())
            .name(correlation.correlationId.value(), 0, correlation.correlationId.length())
            .valueLen(correlationId.length())
            .value(correlationId.value(), 0, correlationId.length());
    }

    public void correlated(
        Array32FW<KafkaHeaderFW> headers,
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> builder,
        int contentLength)
    {
        headers.forEach(h -> correlated(h, builder));

        if (!headers.anyMatch(h -> HTTP_HEADER_NAME_CONTENT_LENGTH.value().equals(h.name().value())) &&
             headers.matchFirst(h -> HTTP_HEADER_NAME_STATUS.value().equals(h.name().value()) &&
                                     HTTP_HEADER_VALUE_204.value().equals(h.value().value())) == null)
        {
            builder.item(i -> i
                    .name(HTTP_HEADER_NAME_CONTENT_LENGTH)
                    .value(Integer.toString(contentLength)));
        }
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
            builder.item(HTTP_CONTENT_LENGTH_0::header);
            async.forEach(a -> builder.item(a::header));
        }
    }

    public boolean async()
    {
        return async != null;
    }

    public boolean idempotent()
    {
        return idempotent;
    }

    public long timeout()
    {
        return timeout;
    }

    public void filters(
        Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder)
    {
        final String16FW correlationId = hash.correlationId();

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
