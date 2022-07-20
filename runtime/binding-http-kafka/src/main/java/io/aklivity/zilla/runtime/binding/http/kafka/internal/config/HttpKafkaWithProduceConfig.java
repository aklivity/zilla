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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public final class HttpKafkaWithProduceConfig
{
    public final String topic;
    public final KafkaAckMode acks;
    public final Optional<String> key;
    public final Optional<List<HttpKafkaWithProduceOverrideConfig>> overrides;
    public final Optional<String> replyTo;
    public final Optional<List<HttpKafkaWithProduceAsyncHeaderConfig>> async;

    private final List<Matcher> asyncMatchers;

    public HttpKafkaWithProduceConfig(
        String topic,
        KafkaAckMode acks,
        String key,
        List<HttpKafkaWithProduceOverrideConfig> overrides,
        String replyTo,
        List<HttpKafkaWithProduceAsyncHeaderConfig> async)
    {
        this.topic = topic;
        this.acks = acks;
        this.key = Optional.ofNullable(key);
        this.overrides = Optional.ofNullable(overrides);
        this.replyTo = Optional.ofNullable(replyTo);
        this.async = Optional.ofNullable(async);

        this.asyncMatchers = this.async.isPresent()
            ? async.stream()
                .map(h -> h.value)
                .filter(v -> v.contains("${correlationId}"))
                .map(HttpKafkaWithProduceConfig::asMatcher)
                .collect(toList())
            : null;
    }

    public String16FW correlationId(
        HttpHeaderFW path)
    {
        final String httpPath = path.value().asString();

        return asyncMatchers != null
            ? asyncMatchers.stream()
                .filter(m -> m.reset(httpPath).matches())
                .map(m -> m.group("correlationId"))
                .map(String16FW::new)
                .findFirst()
                .orElse(null)
            : null;
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern
                .compile(wildcard.replaceAll("\\$\\{(?:params\\.)?([a-zA-Z_]+)\\}", "(?<$1>.+)"))
                .matcher("");
    }
}
