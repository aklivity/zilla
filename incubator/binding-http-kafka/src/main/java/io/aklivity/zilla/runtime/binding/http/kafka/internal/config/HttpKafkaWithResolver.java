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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.stream.HttpBeginExFW;

public final class HttpKafkaWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");

    private final HttpKafkaWithConfig with;
    private final Matcher paramsMatcher;

    private Function<MatchResult, String> replacer = r -> null;

    public HttpKafkaWithResolver(
        HttpKafkaWithConfig with)
    {
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
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

        // TODO: merge
        return new HttpKafkaWithFetchResult(topic, filters);
    }

    public HttpKafkaWithProduceResult resolveProduce(
        HttpBeginExFW httpBeginEx)
    {
        HttpKafkaWithProduceConfig produce = with.produce.get();

        // TODO: hoist to constructor if constant
        String topic0 = produce.topic;
        Matcher topicMatcher = paramsMatcher.reset(topic0);
        if (topicMatcher.matches())
        {
            topic0 = topicMatcher.replaceAll(replacer);
        }
        String16FW topic = new String16FW(topic0);

        DirectBuffer key = null;
        if (produce.key.isPresent())
        {
            String key0 = produce.key.get();
            Matcher keyMatcher = paramsMatcher.reset(key0);
            if (keyMatcher.matches())
            {
                key0 = keyMatcher.replaceAll(replacer);
            }
            key = new String16FW(key0).value();
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
                DirectBuffer value = new String16FW(value0).value();

                overrides.add(new HttpKafkaWithProduceOverrideResult(name, value));
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

        List<HttpKafkaWithProduceAsyncHeaderResult> async = null;
        if (produce.async.isPresent())
        {
            async = new ArrayList<>();

            for (HttpKafkaWithProduceAsyncHeaderConfig header : produce.async.get())
            {
                String name0 = header.name;
                String8FW name = new String8FW(name0);

                String value0 = header.value;
                Matcher valueMatcher = paramsMatcher.reset(value0);
                if (valueMatcher.matches())
                {
                    value0 = valueMatcher.replaceAll(replacer);
                }
                String16FW value = new String16FW(value0);

                async.add(new HttpKafkaWithProduceAsyncHeaderResult(name, value));
            }
        }

        return new HttpKafkaWithProduceResult(topic, key, overrides, replyTo, async);
    }
}
