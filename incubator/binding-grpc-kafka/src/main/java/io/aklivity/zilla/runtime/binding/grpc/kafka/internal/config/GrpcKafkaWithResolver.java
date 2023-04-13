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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class GrpcKafkaWithResolver
{
    private static final Pattern IDENTITY_PATTERN =
            Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\-]*)'\\]).identity\\}");

    private static final String8FW GRPC_METADATA_NAME_IDEMPOTENCY_KEY = new String8FW("idempotency-key");

    private final OctetsFW dashOctetsRW = new OctetsFW().wrap(new String16FW("-").value(), 0, 1);
    private final OctetsFW.Builder octetsRW = new OctetsFW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);
    private final byte[] hashBytesRW = new byte[8192];

    private final GrpcKafkaOptionsConfig options;
    private final LongObjectBiFunction<MatchResult, String> identityReplacer;
    private final GrpcKafkaWithConfig with;
    private final Matcher identityMatcher;

    public GrpcKafkaWithResolver(
        GrpcKafkaOptionsConfig options,
        LongObjectBiFunction<MatchResult, String> identityReplacer,
        GrpcKafkaWithConfig with)
    {
        this.options = options;
        this.identityReplacer = identityReplacer;
        this.with = with;
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
    }

    public GrpcKafkaCapability capability()
    {
        return with.capability;
    }

    public GrpcKafkaWithFetchResult resolveFetch(
        long authorization)
    {
        final GrpcKafkaWithFetchConfig fetch = with.fetch.get();
        String16FW topic = new String16FW(fetch.topic);

        List<GrpcKafkaWithFetchFilterResult> filters = null;
        if (fetch.filters.isPresent())
        {
            filters = new ArrayList<>();

            for (GrpcKafkaWithFetchFilterConfig filter : fetch.filters.get())
            {
                DirectBuffer key = null;
                if (filter.key.isPresent())
                {
                    String key0 = filter.key.get();
                    identityMatcher.reset(key0);
                    if (identityMatcher.matches())
                    {
                        key0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
                    }

                    key = new String16FW(key0).value();
                }

                List<GrpcKafkaWithFetchFilterHeaderResult> headers = null;
                if (filter.headers.isPresent())
                {
                    headers = new ArrayList<>();

                    for (GrpcKafkaWithFetchFilterHeaderConfig header0 : filter.headers.get())
                    {
                        String name0 = header0.name;
                        DirectBuffer name = new String16FW(name0).value();

                        String value0 = header0.value;
                        identityMatcher.reset(value0);
                        if (identityMatcher.matches())
                        {
                            value0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
                        }

                        DirectBuffer value = new String16FW(value0).value();

                        headers.add(new GrpcKafkaWithFetchFilterHeaderResult(name, value));
                    }
                }

                filters.add(new GrpcKafkaWithFetchFilterResult(key, headers));
            }
        }

        return new GrpcKafkaWithFetchResult(topic, filters, options.lastMessageId);
    }

    public GrpcKafkaWithProduceResult resolveProduce(
        long authorization,
        GrpcBeginExFW beginEx)
    {
        GrpcKafkaWithProduceConfig produce = with.produce.get();

        String16FW topic = new String16FW(produce.topic);
        KafkaAckMode acks = produce.acks;

        final GrpcMetadataFW idempotencyKey = beginEx.metadata().matchFirst(m ->
            GRPC_METADATA_NAME_IDEMPOTENCY_KEY.value().compareTo(m.name().value()) == 0);

        final String16FW service = new String16FW(beginEx.service().asString());
        final String16FW method = new String16FW(beginEx.method().asString());

        OctetsFW correlationId = null;
        if (idempotencyKey != null)
        {
            correlationId = new OctetsFW.Builder()
                .wrap(new UnsafeBuffer(new byte[idempotencyKey.valueLen()]), 0, idempotencyKey.valueLen())
                .set(idempotencyKey.value())
                .build();
        }
        else
        {
            final byte[] newIdempotencyKey = UUID.randomUUID().toString().getBytes();
            correlationId = new OctetsFW.Builder()
                .wrap(new UnsafeBuffer(new byte[newIdempotencyKey.length]), 0, newIdempotencyKey.length)
                .set(newIdempotencyKey)
                .build();
        }

        GrpcKafkaWithProduceHash hash = new GrpcKafkaWithProduceHash(octetsRW, dashOctetsRW, correlationId, hashBytesRW);
        hash.digestHash();
        hash.updateHash(beginEx.service().value());
        hash.updateHash(beginEx.method().value());
        hash.updateHash(beginEx.metadata().items());

        Supplier<DirectBuffer> keyRef = () -> null;
        if (produce.key.isPresent())
        {
            String key0 = produce.key.get();

            identityMatcher.reset(key0);
            if (identityMatcher.matches())
            {
                key0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
            }

            String key = key0;
            keyRef = () -> new String16FW(key).value();
        }

        List<GrpcKafkaWithProduceOverrideResult> overrides = null;
        if (produce.overrides.isPresent())
        {
            overrides = new ArrayList<>();

            for (GrpcKafkaWithProduceOverrideConfig override : produce.overrides.get())
            {
                String name0 = override.name;
                DirectBuffer name = new String16FW(name0).value();

                String value0 = override.value;
                Matcher valueMatcher = identityMatcher.reset(value0);
                if (identityMatcher.matches())
                {
                    value0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
                }

                String value = value0;
                Supplier<DirectBuffer> valueRef = () -> new String16FW(value).value();

                overrides.add(new GrpcKafkaWithProduceOverrideResult(name, valueRef, hash::updateHash));
            }
        }

        String16FW replyTo = new String16FW(produce.replyTo);


        return new GrpcKafkaWithProduceResult(service, method, topic, acks, keyRef, overrides, replyTo,
            options.correlation, hash);
    }
}
