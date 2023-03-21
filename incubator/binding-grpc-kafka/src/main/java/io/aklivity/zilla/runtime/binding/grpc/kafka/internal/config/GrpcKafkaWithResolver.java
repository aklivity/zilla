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
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class GrpcKafkaWithResolver
{
    private static final Pattern IDENTITY_PATTERN =
            Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\-]*)'\\]).identity\\}");

    private final String16FW.Builder stringRW = new String16FW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final byte[] hashBytesRW = new byte[8192];

    private final LongObjectBiFunction<MatchResult, String> identityReplacer;
    private final GrpcKafkaWithConfig with;
    private final Matcher identityMatcher;

    public GrpcKafkaWithResolver(
        LongObjectBiFunction<MatchResult, String> identityReplacer,
        GrpcKafkaWithConfig with)
    {
        this.identityReplacer = identityReplacer;
        this.with = with;
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
    }

    public GrpcKafkaWithResult resolve(
        long authorization,
        GrpcBeginExFW grpcBeginEx)
    {
        String16FW topic = new String16FW(with.topic);
        KafkaAckMode acks = with.acks;

        Supplier<DirectBuffer> keyRef = () -> null;
        if (with.key.isPresent())
        {
            String key0 = with.key.get();

            identityMatcher.reset(key0);
            if (identityMatcher.matches())
            {
                key0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
            }

            String key = key0;
            keyRef = () -> new String16FW(key).value();
        }

        GrpcKafkaWithProduceHash hash = new GrpcKafkaWithProduceHash(hashBytesRW);

        String16FW replyTo = new String16FW(with.replyTo);

        List<GrpcKafkaWithFetchFilterResult> filters = null;
        if (with.filters.isPresent())
        {
            filters = new ArrayList<>();

            for (GrpcKafkaWithFetchFilterConfig filter : with.filters.get())
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
        GrpcKafkaCorrelationConfig grpcKafkaCorrelationConfig = new GrpcKafkaCorrelationConfig(replyTo, filters);

        return new GrpcKafkaWithResult(topic, acks, keyRef, hash, grpcKafkaCorrelationConfig);
    }
}
