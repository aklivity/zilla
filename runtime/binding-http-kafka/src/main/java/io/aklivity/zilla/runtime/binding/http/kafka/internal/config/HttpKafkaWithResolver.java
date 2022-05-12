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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.stream.HttpKafkaEtagHelper;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.HttpBeginExFW;

public final class HttpKafkaWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("\\$\\{correlationId\\}");

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

    private final String16FW.Builder stringRW = new String16FW.Builder()
            .wrap(new UnsafeBuffer(new byte[256]), 0, 256);

    private final HttpKafkaEtagHelper etagHelper = new HttpKafkaEtagHelper();

    private final byte[] hashBytesRW = new byte[8192];

    private final HttpKafkaOptionsConfig options;
    private final HttpKafkaWithConfig with;
    private final Matcher paramsMatcher;
    private final Matcher correlationIdMatcher;
    private final Matcher preferWaitMatcher;
    private final Matcher preferAsyncMatcher;
    private final Matcher etagMatcher;

    private Function<MatchResult, String> replacer = r -> null;

    public HttpKafkaWithResolver(
        HttpKafkaOptionsConfig options,
        HttpKafkaWithConfig with)
    {
        this.options = options;
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.correlationIdMatcher = CORRELATION_ID_PATTERN.matcher("");
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
        HttpBeginExFW httpBeginEx)
    {
        HttpKafkaWithFetchConfig fetch = with.fetch.get();

        // TODO: hoist to constructor if constant
        String topic0 = fetch.topic;
        Matcher topicMatcher = paramsMatcher.reset(fetch.topic);
        if (topicMatcher.matches())
        {
            topic0 = topicMatcher.replaceAll(replacer);
        }
        String16FW topic = new String16FW(topic0);

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
                    Matcher keyMatcher = paramsMatcher.reset(key0);
                    if (keyMatcher.matches())
                    {
                        key0 = keyMatcher.replaceAll(replacer);
                    }
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
                        Matcher valueMatcher = paramsMatcher.reset(value0);
                        if (valueMatcher.matches())
                        {
                            value0 = valueMatcher.replaceAll(replacer);
                        }
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

        return new HttpKafkaWithFetchResult(topic, partitions, filters, etag, timeout, merge);
    }

    public HttpKafkaWithProduceResult resolveProduce(
        HttpBeginExFW httpBeginEx)
    {
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

                String value0 = header.value;
                Matcher valueMatcher = paramsMatcher.reset(value0);
                if (valueMatcher.find())
                {
                    value0 = valueMatcher.replaceAll(replacer);
                }

                String value = value0;
                Supplier<String16FW> valueRef = () -> new String16FW(value);

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

                        return new String16FW(value1);
                    };
                }

                async.add(new HttpKafkaWithProduceAsyncHeaderResult(name, valueRef));
            }
        }

        // TODO: hoist to constructor if constant
        String topic0 = produce.topic;
        Matcher topicMatcher = paramsMatcher.reset(topic0);
        if (topicMatcher.matches())
        {
            topic0 = topicMatcher.replaceAll(replacer);
        }
        String16FW topic = new String16FW(topic0);

        Supplier<DirectBuffer> keyRef = () -> null;
        if (produce.key.isPresent())
        {
            String key0 = produce.key.get();
            Matcher keyMatcher = paramsMatcher.reset(key0);
            if (keyMatcher.matches())
            {
                key0 = keyMatcher.replaceAll(replacer);
            }
            String key = key0;
            keyRef = () -> new String16FW(key).value();

            if (correlationId != null)
            {
                keyRef = () ->
                {
                    String key1 = key;
                    Matcher key1Matcher = correlationIdMatcher.reset(key1);
                    if (key1Matcher.find())
                    {
                        key1 = key1Matcher.replaceAll(hash.correlationId().asString());
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
            ifMatch = new String16FW(etagMatcher.group("ifmatch"));
        }

        return new HttpKafkaWithProduceResult(
                options.correlation, topic, keyRef, overrides, ifMatch, replyTo,
                idempotencyKey, async, hash, timeout);
    }
}
