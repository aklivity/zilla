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

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.stream.GrpcBeginExFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;

public final class GrpcKafkaWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");
    private static final Pattern IDENTITY_PATTERN =
            Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\-]*)'\\]).identity\\}");

    private final String16FW.Builder stringRW = new String16FW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);


    private final byte[] hashBytesRW = new byte[8192];

    private final GrpcKafkaOptionsConfig options;
    private final LongObjectBiFunction<MatchResult, String> identityReplacer;

    private Function<MatchResult, String> replacer = r -> null;

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

    public void onConditionMatched(
        GrpcKafkaConditionMatcher condition)
    {
        this.replacer = r -> condition.parameter(r.group(1));
    }



    public GrpcKafkaWithProduceResult resolveProduce(
        long authorization,
        GrpcBeginExFW httpBeginEx)
    {

        final String16FW idempotencyKey = new String16FW(UUID.randomUUID().toString();

        GrpcKafkaWithProduceHash hash = new GrpcKafkaWithProduceHash(hashBytesRW);


        // TODO: hoist to constructor if constant
        String topic0 = produce.topic;
        Matcher topicMatcher = paramsMatcher.reset(topic0);
        if (topicMatcher.matches())
        {
            topic0 = topicMatcher.replaceAll(replacer);
        }
        String16FW topic = new String16FW(topic0);

        KafkaAckMode acks = produce.acks;

        Supplier<DirectBuffer> keyRef = () -> null;
        if (produce.key.isPresent())
        {
            String key0 = produce.key.get();
            Matcher keyMatcher = paramsMatcher.reset(key0);
            if (keyMatcher.matches())
            {
                key0 = keyMatcher.replaceAll(replacer);
            }

            keyMatcher = identityMatcher.reset(key0);
            if (identityMatcher.matches())
            {
                key0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
            }

            String key = key0;
            keyRef = () -> new String16FW(key).value();

            if (correlationId != null)
            {
                keyRef = () ->
                {
                    String key1 = key;
                    Matcher key1Matcher = idempotencyKeyMatcher.reset(key1);
                    if (key1Matcher.find())
                    {
                        key1 = key1Matcher.replaceAll(idempotencyKey.asString());
                    }
                    return new String16FW(key1).value();
                };
            }
        }

        List<HttpKafkaWithProduceOverrideResult> overrides = null;
        if (produce.overrides.isPresent())
        {
            overrides = new ArrayList<>();

            for (HttpKafkaWithProduceOverrideConfig override : produce.overrides.get())
            {
                String name0 = override.name;
                DirectBuffer name = new String16FW(name0).value();

                String value0 = override.value;
                Matcher valueMatcher = paramsMatcher.reset(value0);
                if (valueMatcher.matches())
                {
                    value0 = valueMatcher.replaceAll(replacer);
                }

                valueMatcher = identityMatcher.reset(value0);
                if (identityMatcher.matches())
                {
                    value0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
                }

                String value = value0;
                Supplier<DirectBuffer> valueRef = () -> new String16FW(value).value();
                if (correlationId != null)
                {
                    valueRef = () ->
                    {
                        String value1 = value;
                        Matcher value1Matcher = correlationIdMatcher.reset(value1);
                        if (value1Matcher.find())
                        {
                            value1 = value1Matcher.replaceAll(hash.correlationId().asString());
                        }
                        return new String16FW(value1).value();
                    };
                }

                overrides.add(new HttpKafkaWithProduceOverrideResult(name, valueRef, hash::updateHash));
            }
        }

        String16FW replyTo = null;
        if (produce.replyTo.isPresent())
        {
            String replyTo0 = produce.replyTo.get();
            Matcher replyToMatcher = paramsMatcher.reset(replyTo0);
            if (replyToMatcher.matches())
            {
                replyTo0 = replyToMatcher.replaceAll(replacer);
            }
            replyTo = new String16FW(replyTo0);
        }

        String16FW ifMatch = null;

        final HttpHeaderFW httpIfMatch = httpHeaders.matchFirst(h -> HEADER_NAME_IF_MATCH.equals(h.name()));
        if (httpIfMatch != null && etagMatcher.reset(httpIfMatch.value().asString()).matches())
        {
            final String group = etagMatcher.group("ifmatch");
            if (group != null)
            {
                ifMatch = new String16FW(group);
            }
        }

        return new HttpKafkaWithProduceResult(
                options.correlation, topic, acks, keyRef, overrides, ifMatch, replyTo,
                idempotencyKey, async, hash, timeout);
    }
}
