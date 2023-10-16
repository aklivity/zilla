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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;

public class MqttKafkaConditionMatcher
{
    private final Matcher topicMatcher;
    public final String topic;

    public MqttKafkaConditionMatcher(
        MqttKafkaConditionConfig condition)
    {
        this.topic = condition.topic;
        this.topicMatcher = condition.topic != null ? asTopicMatcher(condition.topic) : null;
    }

    public boolean matches(
        CharSequence topic)
    {
        return matchTopic(topic);
    }

    private boolean matchTopic(
        CharSequence topic)
    {
        return this.topicMatcher == null || this.topicMatcher.reset(topic).matches();
    }


    public static String generateRegexPattern(
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
            if ("*".equals(currentPart))
            {
                currentPart = ".*";
            }
            pattern = (level > 0) ? "(\\/\\+|\\/" + currentPart + ")" : "(\\+|" + currentPart + ")";
        }

        String nextPart = generateRegexPattern(remainingParts, level + 1, fixedLength);
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

    private static Matcher asTopicMatcher(
        String wildcard)
    {
        String patternBegin = wildcard.startsWith("/") ? "(" : "^(?!\\/)(";
        String fixedPattern = patternBegin + generateRegexPattern(wildcard, 0, true) + ")?\\/?\\#?";
        String nonFixedPattern = patternBegin + generateRegexPattern(wildcard, 0, false) + ")?\\/?\\#";
        return Pattern.compile(nonFixedPattern + "|" + fixedPattern).matcher("");
    }
}
