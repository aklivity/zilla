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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.sse.kafka.internal.stream.SseKafkaIdHelper;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.sse.kafka.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;

public final class SseKafkaWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");
    private static final Pattern IDENTITY_PATTERN =
            Pattern.compile("\\$\\{guarded(?:\\['([a-zA-Z]+[a-zA-Z0-9\\._\\-]*)'\\]).identity\\}");
    private final LongObjectBiFunction<MatchResult, String> identityReplacer;
    private final SseKafkaWithConfig with;
    private final Matcher paramsMatcher;
    private final Matcher identityMatcher;

    private Function<MatchResult, String> paramsReplacer = r -> null;

    public SseKafkaWithResolver(
        LongObjectBiFunction<MatchResult, String> identityReplacer,
        SseKafkaWithConfig with)
    {
        this.identityReplacer = identityReplacer;
        this.with = with;
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.identityMatcher = IDENTITY_PATTERN.matcher("");
    }

    public void onConditionMatched(
        SseKafkaConditionMatcher condition)
    {
        this.paramsReplacer = r -> condition.parameter(r.group(1));
    }

    public SseKafkaWithResult resolve(
        long authorization,
        SseBeginExFW sseBeginEx, SseKafkaIdHelper sseEventId)
    {
        final String8FW lastId = sseBeginEx != null ? sseBeginEx.lastId() : null;
        final DirectBuffer progress64 = sseEventId.findProgress(lastId);
        final Array32FW<KafkaOffsetFW> partitions = sseEventId.decode(progress64);

        // TODO: hoist to constructor if constant
        String topic0 = with.topic;
        Matcher topicMatcher = paramsMatcher.reset(with.topic);
        if (topicMatcher.matches())
        {
            topic0 = topicMatcher.replaceAll(paramsReplacer);
        }
        String16FW topic = new String16FW(topic0);

        List<SseKafkaWithFilterResult> filters = null;
        if (with.filters.isPresent())
        {
            filters = new ArrayList<>();

            for (SseKafkaWithFilterConfig filter : with.filters.get())
            {
                DirectBuffer key = null;
                if (filter.key.isPresent())
                {
                    String key0 = filter.key.get();
                    Matcher keyMatcher = paramsMatcher.reset(key0);
                    if (keyMatcher.matches())
                    {
                        key0 = keyMatcher.replaceAll(paramsReplacer);
                    }

                    keyMatcher = identityMatcher.reset(key0);
                    if (identityMatcher.matches())
                    {
                        key0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
                    }

                    key = new String16FW(key0).value();
                }

                List<SseKafkaWithFilterHeaderResult> headers = null;
                if (filter.headers.isPresent())
                {
                    headers = new ArrayList<>();

                    for (SseKafkaWithFilterHeaderConfig header0 : filter.headers.get())
                    {
                        String name0 = header0.name;
                        DirectBuffer name = new String16FW(name0).value();

                        String value0 = header0.value;
                        Matcher valueMatcher = paramsMatcher.reset(value0);
                        if (valueMatcher.matches())
                        {
                            value0 = valueMatcher.replaceAll(paramsReplacer);
                        }

                        valueMatcher = identityMatcher.reset(value0);
                        if (identityMatcher.matches())
                        {
                            value0 = identityMatcher.replaceAll(r -> identityReplacer.apply(authorization, r));
                        }

                        DirectBuffer value = new String16FW(value0).value();

                        headers.add(new SseKafkaWithFilterHeaderResult(name, value));
                    }
                }

                filters.add(new SseKafkaWithFilterResult(key, headers));
            }
        }

        String eventId = with.eventId;

        return new SseKafkaWithResult(topic, partitions, filters, eventId);
    }
}
