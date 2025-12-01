/*
 * Copyright 2021-2024 Aklivity Inc
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

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchFilterHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithFetchMergeConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceAsyncHeaderConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.config.HttpKafkaWithProduceOverrideConfig;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.stream.HttpKafkaEtagHelper;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class HttpKafkaWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");
    private static final Pattern IDENTITY_PATTERN =
            Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).identity\\}");
    private static final Pattern ATTRIBUTE_PATTERN =
        Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)'\\]).attributes" +
            ".([a-zA-Z]+[a-zA-Z0-9\\._\\:\\-]*)\\}");
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("\\$\\{correlationId\\}");
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("\\$\\{idempotencyKey\\}");

    private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
    private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
    private static final String8FW HEADER_NAME_PREFER = new String8FW("prefer");
    private static final String8FW HEADER_NAME_IF_MATCH = new String8FW("if-match");
    private static final String8FW HEADER_NAME_IF_NONE_MATCH = new String8FW("if-none-match");

    private static final String16FW HEADER_VALUE_METHOD_GET = new String16FW("GET");
    private static final String16FW HEADER_VALUE_CONTENT_TYPE_APPLICATION_JSON = new String16FW("application/json");

    private static final Pattern HEADER_VALUE_PREFER_WAIT_PATTERN =
            Pattern.compile("(^|;)\\s*wait=(?<wait>\\d+)(;|$)");
    private static final Pattern HEADER_VALUE_PREFER_ASYNC_PATTERN =
            Pattern.compile("(^|;)\\s*respond-async(;|$)");
    private static final Pattern HEADER_VALUE_ETAG_PATTERN =
            Pattern.compile("(?<etag>(?<progress>[a-zA-Z0-9\\-_=]+)(/(?<ifmatch>.*))?)");

    private static final OctetsFW MERGE_HEADER_JSON_ARRAY = new OctetsFW().wrap(new String8FW("[").value(), 0, 1);
    private static final OctetsFW MERGE_SEPARATOR_JSON_ARRAY = new OctetsFW().wrap(new String8FW(",").value(), 0, 1);
    private static final OctetsFW MERGE_TRAILER_JSON_ARRAY = new OctetsFW().wrap(new String8FW("]").value(), 0, 1);

    public static Set<String> extractGuardNames(
        HttpKafkaWithConfig withConfig)
    {
        // Helper to extract guard names from a string using both identity and attribute patterns
        Function<String, Stream<String>> extractGuards = value ->
        {
            Stream.Builder<String> builder = Stream.builder();

            Matcher identityMatcher = IDENTITY_PATTERN.matcher(value);
            while (identityMatcher.find())
            {
                builder.add(identityMatcher.group(1));
            }

            Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher(value);
            while (attributeMatcher.find())
            {
                builder.add(attributeMatcher.group(1));
            }

            return builder.build();
        };

        // Extract from produce configuration
        Stream<String> produceGuards = withConfig.produce
            .stream()
            .flatMap(produce ->
            {
                Stream.Builder<Stream<String>> streams = Stream.builder();

                // Extract from topic
                streams.add(extractGuards.apply(produce.topic));

                // Extract from key
                produce.key.ifPresent(key -> streams.add(extractGuards.apply(key)));

                // Extract from overrides
                produce.overrides.ifPresent(overrides ->
                    streams.add(overrides.stream()
                        .flatMap(override -> extractGuards.apply(override.value))));

                return streams.build().flatMap(s -> s);
            });

        // Extract from fetch configuration
        Stream<String> fetchGuards = withConfig.fetch
            .stream()
            .flatMap(fetch ->
            {
                Stream.Builder<Stream<String>> streams = Stream.builder();

                // Extract from topic
                streams.add(extractGuards.apply(fetch.topic));

                // Extract from filters
                fetch.filters.ifPresent(filters ->
                    streams.add(filters.stream()
                        .flatMap(filter ->
                        {
                            Stream.Builder<Stream<String>> filterStreams = Stream.builder();

                            // Extract from filter key
                            filter.key.ifPresent(key -> filterStreams.add(extractGuards.apply(key)));

                            // Extract from filter headers
                            filter.headers.ifPresent(headers ->
                                filterStreams.add(headers.stream()
                                    .flatMap(header -> extractGuards.apply(header.value))));

                            return filterStreams.build().flatMap(s -> s);
                        })));

                return streams.build().flatMap(s -> s);
            });

        return Stream.concat(produceGuards, fetchGuards)
            .collect(toSet());
    }

    private final String16FW.Builder stringRW = new String16FW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final HttpKafkaEtagHelper etagHelper = new HttpKafkaEtagHelper();

    private final byte[] hashBytesRW = new byte[8192];

    private final HttpKafkaOptionsConfig options;
    private final LongObjectBiFunction<MatchResult, String> identityReplacer;
    private final LongObjectBiFunction<MatchResult, String> attributeReplacer;
    private final HttpKafkaWithConfig with;
    private final Matcher paramsMatcher;
    private final Matcher identityMatcher;
    private final Matcher attributeMatcher;
    private final Matcher correlationIdMatcher;
    private final Matcher idempotencyKeyMatcher;
    private final Matcher preferWaitMatcher;
    private final Matcher preferAsyncMatcher;
    private final Matcher etagMatcher;

    private Function<MatchResult, String> replacer = r -> null;

    public HttpKafkaWithResolver(
        HttpKafkaOptionsConfig options,
        LongObjectBiFunction<MatchResult, String> identityReplacer,
        LongObjectBiFunction<MatchResult, String> attributeReplacer,
        HttpKafkaWithConfig with)
    {
        this.options = options;
        this.identityReplacer = identityReplacer;
        this.attributeReplacer = attributeReplacer;
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
        this.attributeMatcher = ATTRIBUTE_PATTERN.matcher("");
        this.correlationIdMatcher = CORRELATION_ID_PATTERN.matcher("");
        this.idempotencyKeyMatcher = IDEMPOTENCY_KEY_PATTERN.matcher("");
        this.preferWaitMatcher = HEADER_VALUE_PREFER_WAIT_PATTERN.matcher("");
        this.preferAsyncMatcher = HEADER_VALUE_PREFER_ASYNC_PATTERN.matcher("");
        this.etagMatcher = HEADER_VALUE_ETAG_PATTERN.matcher("");
    }

    public void onConditionMatched(
        HttpKafkaConditionMatcher condition)
    {
        this.replacer = r -> condition.parameter(r.group(1));
    }

    public HttpKafkaCapability capability()
    {
        return with.capability;
    }

    public HttpKafkaWithFetchResult resolveFetch(
        long authorization,
        HttpBeginExFW httpBeginEx)
    {
        long compositeId = with.compositeId;
        HttpKafkaWithFetchConfig fetch = with.fetch.get();

        // TODO: hoist to constructor if constant
        String16FW topic = resolveTopic(authorization, fetch.topic);

        long timeout = 0L;
        Array32FW<KafkaOffsetFW> partitions = null;
        String16FW etag = null;

        final Array32FW<HttpHeaderFW> httpHeaders = httpBeginEx.headers();
        final HttpHeaderFW prefer = httpHeaders.matchFirst(h -> HEADER_NAME_PREFER.equals(h.name()));
        if (prefer != null && preferWaitMatcher.reset(prefer.value().asString()).find())
        {
            timeout = SECONDS.toMillis(Long.parseLong(preferWaitMatcher.group("wait")));
        }

        final HttpHeaderFW ifNoneMatch = httpHeaders.matchFirst(h -> HEADER_NAME_IF_NONE_MATCH.equals(h.name()));
        if (ifNoneMatch != null && etagMatcher.reset(ifNoneMatch.value().asString()).matches())
        {
            etag = new String16FW(etagMatcher.group("etag"));

            final String progress = etagMatcher.group("progress");
            final String16FW decodable = stringRW
                .set(ifNoneMatch.value().value(), 0, progress.length())
                .build();
            partitions = fetch.merge.isPresent()
                ? etagHelper.decodeLatest(decodable)
                : timeout != 0L ? etagHelper.decodeLive(decodable) : etagHelper.decodeHistorical(decodable);
        }

        List<HttpKafkaWithFetchFilterResult> filters = null;
        if (fetch.filters.isPresent())
        {
            filters = new ArrayList<>();

            for (HttpKafkaWithFetchFilterConfig filter : fetch.filters.get())
            {
                DirectBuffer key = null;
                if (filter.key.isPresent())
                {
                    String key0 = filter.key.get();
                    key0 = findAndReplace(key0, paramsMatcher, replacer);
                    key0 = findAndReplace(key0, identityMatcher, r -> identityReplacer.apply(authorization, r));
                    key0 = findAndReplace(key0, attributeMatcher, r -> attributeReplacer.apply(authorization, r));

                    key = new String16FW(key0).value();
                }

                List<HttpKafkaWithFetchFilterHeaderResult> headers = null;
                if (filter.headers.isPresent())
                {
                    headers = new ArrayList<>();

                    for (HttpKafkaWithFetchFilterHeaderConfig header0 : filter.headers.get())
                    {
                        String name0 = header0.name;
                        DirectBuffer name = new String16FW(name0).value();

                        String value0 = header0.value;
                        value0 = findAndReplace(value0, paramsMatcher, replacer);
                        value0 = findAndReplace(value0, identityMatcher, r -> identityReplacer.apply(authorization, r));
                        value0 = findAndReplace(value0, attributeMatcher, r -> attributeReplacer.apply(authorization, r));

                        DirectBuffer value = new String16FW(value0).value();

                        headers.add(new HttpKafkaWithFetchFilterHeaderResult(name, value));
                    }
                }

                filters.add(new HttpKafkaWithFetchFilterResult(key, headers));
            }
        }

        HttpKafkaWithFetchMergeResult merge = null;
        if (fetch.merge.isPresent())
        {
            final HttpKafkaWithFetchMergeConfig config = fetch.merge.get();

            // schema requires application/json
            assert HEADER_VALUE_CONTENT_TYPE_APPLICATION_JSON.asString().equals(config.contentType);

            final String16FW contentType = HEADER_VALUE_CONTENT_TYPE_APPLICATION_JSON;
            OctetsFW header = MERGE_HEADER_JSON_ARRAY;
            OctetsFW separator = MERGE_SEPARATOR_JSON_ARRAY;
            OctetsFW trailer = MERGE_TRAILER_JSON_ARRAY;

            merge = new HttpKafkaWithFetchMergeResult(contentType, header, separator, trailer);
        }

        return new HttpKafkaWithFetchResult(compositeId, topic, partitions, filters, etag, timeout, merge);
    }

    public HttpKafkaWithProduceResult resolveProduce(
        long authorization,
        HttpBeginExFW httpBeginEx)
    {
        long compositeId = with.compositeId;
        HttpKafkaWithProduceConfig produce = with.produce.get();

        final Array32FW<HttpHeaderFW> httpHeaders = httpBeginEx.headers();
        List<HttpKafkaWithProduceAsyncHeaderResult> async = null;
        String16FW correlationId = null;
        long timeout = 0L;

        final HttpHeaderFW prefer = httpHeaders.matchFirst(h -> HEADER_NAME_PREFER.equals(h.name()));
        final String preferValue = prefer != null ? prefer.value().asString() : null;

        if (produce.async.isPresent())
        {
            final HttpHeaderFW httpMethod = httpHeaders.matchFirst(h -> HEADER_NAME_METHOD.equals(h.name()));
            if (HEADER_VALUE_METHOD_GET.equals(httpMethod.value()))
            {
                final HttpHeaderFW httpPath = httpHeaders.matchFirst(h -> HEADER_NAME_PATH.equals(h.name()));
                correlationId = produce.correlationId(httpPath);

                if (preferValue != null && preferWaitMatcher.reset(preferValue).find())
                {
                    timeout = SECONDS.toMillis(Long.parseLong(preferWaitMatcher.group("wait")));
                }
            }
        }

        final String16FW asyncId = correlationId;

        final HttpHeaderFW httpIdempotencyKey = httpHeaders.matchFirst(h -> options.idempotency.header.equals(h.name()));
        final String16FW idempotencyKey = correlationId == null && httpIdempotencyKey != null
                ? new String16FW(httpIdempotencyKey.value().asString())
                : correlationId == null
                    ? new String16FW(UUID.randomUUID().toString())
                    : null;

        if (correlationId == null)
        {
            correlationId = idempotencyKey;
        }

        HttpKafkaWithProduceHash hash = new HttpKafkaWithProduceHash(correlationId, hashBytesRW);

        if (produce.async.isPresent() &&
            (asyncId != null || preferValue != null && preferAsyncMatcher.reset(preferValue).find()))
        {
            async = new ArrayList<>();

            for (HttpKafkaWithProduceAsyncHeaderConfig header : produce.async.get())
            {
                String name0 = header.name;
                String8FW name = new String8FW(name0);

                String value0 = findAndReplace(header.value, paramsMatcher, replacer);

                String value = value0;
                Supplier<String16FW> valueRef = () -> new String16FW(value);

                if (correlationId != null)
                {
                    valueRef = () ->
                    {
                        String value1 = value;
                        value1 = findAndReplace(value1, correlationIdMatcher, r -> hash.correlationId().asString());

                        return new String16FW(value1);
                    };
                }

                async.add(new HttpKafkaWithProduceAsyncHeaderResult(name, valueRef));
            }
        }

        // TODO: hoist to constructor if constant
        String16FW topic = resolveTopic(authorization, produce.topic);

        KafkaAckMode acks = produce.acks;

        Supplier<DirectBuffer> keyRef = () -> null;
        if (produce.key.isPresent())
        {
            String key0 = produce.key.get();
            key0 = findAndReplace(key0, paramsMatcher, replacer);
            key0 = findAndReplace(key0, identityMatcher, r -> identityReplacer.apply(authorization, r));
            key0 = findAndReplace(key0, attributeMatcher, r -> attributeReplacer.apply(authorization, r));

            String key = key0;
            keyRef = () -> new String16FW(key).value();

            if (correlationId != null)
            {
                keyRef = () ->
                {
                    String key1 = key;
                    key1 = findAndReplace(key1, idempotencyKeyMatcher, r -> idempotencyKey.asString());
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
                value0 = findAndReplace(value0, paramsMatcher, replacer);
                value0 = findAndReplace(value0, identityMatcher, r -> identityReplacer.apply(authorization, r));
                value0 = findAndReplace(value0, attributeMatcher, r -> attributeReplacer.apply(authorization, r));

                String value = value0;
                Supplier<DirectBuffer> valueRef = () -> new String16FW(value).value();
                if (correlationId != null)
                {
                    valueRef = () ->
                    {
                        String value1 = value;
                        value1 = findAndReplace(value1, idempotencyKeyMatcher, r -> idempotencyKey.asString());
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
            replyTo0 = findAndReplace(replyTo0, paramsMatcher, replacer);
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
                compositeId, options.correlation, topic, acks, keyRef, overrides, ifMatch, replyTo,
                produce.correlationId, idempotencyKey, async, hash, timeout);
    }

    private String16FW resolveTopic(
        long authorization,
        String topic)
    {
        topic = findAndReplace(topic, paramsMatcher, replacer);
        topic = findAndReplace(topic, identityMatcher, r -> identityReplacer.apply(authorization, r));
        topic = findAndReplace(topic, attributeMatcher, r -> attributeReplacer.apply(authorization, r));

        return new String16FW(topic);
    }

    private static String findAndReplace(
        String value,
        Matcher matcher,
        Function<MatchResult, String> replacer)
    {
        matcher.reset(value);
        while (matcher.find())
        {
            value = matcher.replaceAll(replacer);
            matcher.reset(value);
        }
        return value;
    }
}
