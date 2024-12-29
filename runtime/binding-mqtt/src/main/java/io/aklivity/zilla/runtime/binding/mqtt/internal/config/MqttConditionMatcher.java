/*
 * Copyright 2021-2024 Aklivity Inc.
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
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;

public final class MqttConditionMatcher
{
    private final List<Matcher> sessionMatchers;
    private final List<String> subscribeTopics;
    private final List<String> publishTopics;
    private final List<GuardedConfig> guarded;

    public MqttConditionMatcher(
        MqttConditionConfig condition,
        List<GuardedConfig> guarded)
    {
        this.sessionMatchers =
            condition.sessions != null && !condition.sessions.isEmpty() ?
                asWildcardMatcher(condition.sessions.stream().map(s -> s.clientId).collect(Collectors.toList())) : null;
        this.subscribeTopics =
            condition.subscribes != null && !condition.subscribes.isEmpty() ?
                condition.subscribes.stream().map(s -> s.topic).collect(Collectors.toList()) : null;
        this.publishTopics =
            condition.publishes != null && !condition.publishes.isEmpty() ?
                condition.publishes.stream().map(s -> s.topic).collect(Collectors.toList()) : null;
        this.guarded = guarded;
    }

    public boolean matchesSession(
        String clientId)
    {
        boolean match = sessionMatchers == null;
        if (!match)
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
        String topic,
        long authorization)
    {
        boolean match = false;
        if (subscribeTopics != null)
        {
            for (String subscribeTopic : subscribeTopics)
            {
                match = topicMatches(topic, subscribeTopic, authorization);
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    public boolean matchesPublish(
        String topic,
        long authorization)
    {
        boolean match = false;
        if (publishTopics != null)
        {
            for (String publishTopic : publishTopics)
            {
                match = topicMatches(topic, publishTopic, authorization);
                if (match)
                {
                    break;
                }
            }
        }
        return match;
    }

    private boolean topicMatches(String topic, String pattern, long authorization)
    {
        int topicIndex = 0;
        for (int i = 0; i < pattern.length(); ++i)
        {
            char patternChar = pattern.charAt(i);
            if (patternChar == '#')
            {
                return true;
            }
            else if (patternChar == '+')
            {
                while (topicIndex < topic.length() && topic.charAt(topicIndex) != '/')
                {
                    topicIndex++;
                }
            }
            else
            {
                if (pattern.startsWith("{guarded[", i))
                {
                    int i2 = i + "{guarded[".length();
                    GuardedConfig guardedMatch = null;
                    for (GuardedConfig g : guarded)
                    {
                        if (pattern.startsWith(g.name, i2))
                        {
                            guardedMatch = g;
                            i2 += g.name.length();
                            break;
                        }
                    }
                    if (guardedMatch != null && pattern.startsWith("].identity}", i2))
                    {
                        String identity = guardedMatch.identity.apply(authorization);
                        if (identity != null && topic.startsWith(identity, topicIndex))
                        {
                            i = i2 + "].identity}".length();
                            topicIndex += identity.length();
                            continue;
                        }
                    }
                }
                if (topicIndex == topic.length() || topic.charAt(topicIndex++) != patternChar)
                {
                    return false;
                }
            }
        }
        return topicIndex == topic.length();
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
}
