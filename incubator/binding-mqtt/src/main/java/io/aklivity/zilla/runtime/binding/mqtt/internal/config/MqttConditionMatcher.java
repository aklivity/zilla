/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.mqtt.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mqtt.config.MqttConditionConfig;

public final class MqttConditionMatcher
{
    private final List<Matcher> sessionMatchers;
    private final List<Matcher> subscribeMatchers;
    private final List<Matcher> publishMatchers;

    public MqttConditionMatcher(
        MqttConditionConfig condition)
    {
        this.sessionMatchers =
            condition.sessions != null ?
                asWildcardMatcher(condition.sessions.stream().map(s -> s.clientId).collect(Collectors.toList())) : null;
        this.subscribeMatchers =
            condition.subscribes != null ?
                asTopicMatcher(condition.subscribes.stream().map(s -> s.topic).collect(Collectors.toList())) : null;
        this.publishMatchers =
            condition.publishes != null ?
                asTopicMatcher(condition.publishes.stream().map(s -> s.topic).collect(Collectors.toList())) : null;
    }

    public boolean matchesSession(
        String clientId)
    {
        boolean match = false;
        if (sessionMatchers != null)
        {
            for (Matcher matcher : sessionMatchers)
            {
                match = matcher.reset(clientId).matches();
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    public boolean matchesSubscribe(
        String topic)
    {
        boolean match = false;
        if (subscribeMatchers != null)
        {
            for (Matcher matcher : subscribeMatchers)
            {
                match = matcher.reset(topic).matches();
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    public boolean matchesPublish(
        String topic)
    {
        boolean match = false;
        if (publishMatchers != null)
        {
            for (Matcher matcher : publishMatchers)
            {
                match = matcher.reset(topic).matches();
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    private static List<Matcher> asWildcardMatcher(
        List<String> wildcards)
    {
        List<Matcher> matchers = new ArrayList<>();
        for (String wildcard : wildcards)
        {
            String pattern = wildcard.replace(".", "\\.").replace("*", ".*");

            if (!pattern.endsWith(".*"))
            {
                pattern = pattern + "(\\?.*)?";
            }
            matchers.add(Pattern.compile(pattern).matcher(""));

        }

        return matchers;
    }

    private static List<Matcher> asTopicMatcher(
        List<String> wildcards)
    {
        List<Matcher> matchers = new ArrayList<>();
        for (String wildcard : wildcards)
        {
            matchers.add(Pattern.compile(wildcard
                .replace(".", "\\.")
                .replace("$", "\\$")
                .replace("+", "[^/]*")
                .replace("#", ".*")).matcher(""));

        }
        return matchers;
    }
}
