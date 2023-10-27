/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;

public class MqttKafkaConditionMatcher
{
    public final List<Matcher> subscribeMatchers;
    private final List<Matcher> publishMatchers;

    public MqttKafkaConditionMatcher(
        MqttKafkaConditionConfig condition)
    {
        this.subscribeMatchers =
            condition.subscribes != null && !condition.subscribes.isEmpty() ?
                asTopicMatcher(condition.subscribes.stream().map(s -> s.topic).collect(Collectors.toList())) : null;
        this.publishMatchers =
            condition.publishes != null && !condition.publishes.isEmpty() ?
                asTopicMatcher(condition.publishes.stream().map(s -> s.topic).collect(Collectors.toList())) : null;
    }

    public boolean matchesSubscribe(
        String topic)
    {
        boolean match = false;
        if (subscribeMatchers != null)
        {
            for (Matcher matcher : subscribeMatchers)
            {
                if (matcher.reset(topic).matches())
                {
                    match = true;
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
                if (matcher.reset(topic).matches())
                {
                    match = true;
                    break;
                }
            }
        }
        return match;
    }


    private static List<Matcher> asTopicMatcher(
        List<String> wildcards)
    {
        final List<Matcher> matchers = new ArrayList<>();
        for (String wildcard : wildcards)
        {
            String patternBegin = wildcard.startsWith("/") ? "(" : "^(?!\\/)(";
            String fixedPattern = patternBegin + asRegexPattern(wildcard, 0, true) + ")?\\/?\\#?";
            String nonFixedPattern = patternBegin + asRegexPattern(wildcard, 0, false) + ")?\\/?\\#";
            matchers.add(Pattern.compile(nonFixedPattern + "|" + fixedPattern).matcher(""));
        }
        return matchers;
    }

    private static String asRegexPattern(
        String wildcard,
        int level,
        boolean fixedLength)
    {
        if (wildcard.isEmpty())
        {
            return "";
        }

        String[] parts = wildcard.split("/", 2);
        String currentPart = parts[0];
        String remainingParts = (parts.length > 1) ? parts[1] : "";

        String pattern;
        if ("".equals(currentPart))
        {
            pattern = "\\/";
            level--;
        }
        else
        {
            currentPart = currentPart
                .replace(".", "\\.")
                .replace("$", "\\$")
                .replace("+", "[^/]*")
                .replace("#", ".*");
            pattern = (level > 0) ? "(\\/\\+|\\/" + currentPart + ")" : "(\\+|" + currentPart + ")";
        }

        String nextPart = asRegexPattern(remainingParts, level + 1, fixedLength);
        if (level > 0)
        {
            pattern = "(" + pattern;
        }
        pattern += nextPart;

        if ("".equals(nextPart))
        {
            String endParentheses = fixedLength ? ")" : ")?";
            pattern += "(\\/\\#)?" + endParentheses.repeat(Math.max(0, level));
        }
        return pattern;
    }
}
