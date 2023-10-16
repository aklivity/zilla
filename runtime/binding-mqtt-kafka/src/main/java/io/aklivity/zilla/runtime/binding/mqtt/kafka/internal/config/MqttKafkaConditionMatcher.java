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
import java.util.Arrays;
import java.util.List;
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


    public static String generateRegexPattern(String hierarchicalString, int level) {
        if (hierarchicalString.isEmpty()) {
            return "";
        }
        //        (\/(\+|some)((\/\+|\/hierarchical)((\/\+|\/topic)(\/\+|\/name)(\/\#)?))?)?\/?\#?

        String[] parts = hierarchicalString.split("/", 2);
        String currentPart = parts[0];
        String remainingParts = (parts.length > 1) ? parts[1] : "";

        String pattern;
        if (currentPart.equals(""))
        {
            pattern = "(\\/";
            // decrease level so at the next level we recognise that we're at the first non-empty segment
            level--;
        }
        else
        {
            if (level > 0)
            {
                pattern = "(\\/\\+|\\/" + currentPart + ")";
            }
            else
            {
                pattern = "(\\+|" + currentPart + ")";
            }
        }
        String nextPart = generateRegexPattern(remainingParts, level + 1);
        if (level > 0  && !nextPart.equals(""))
        {
            pattern = "(" + pattern;
        }
        pattern += nextPart;

        return pattern;
    }

    private static Matcher asTopicMatcher(
        String wildcard)
    {
        List<String> patterns = new ArrayList<>();
        String[] filterLevels = wildcard.split("/");

        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < filterLevels.length; i++)
        {
            String level = filterLevels[i];

            if (level.isEmpty())
            {
                patterns.add("/#");
            }
            else
            {
                if (i > 0)
                {
                    regex.append("/");
                }
                if ("*".equals(level))
                {
                    regex.append(".*");
                    patterns.add(regex.toString());
                }
                else
                {
                    regex.append("(").append(level).append("|\\+)");
                    if (i == filterLevels.length - 1)
                    {
                        patterns.add(regex.toString());
                    }
                    patterns.add(regex + "/#");
                }
            }
        }

        patterns.add(0, "#");
        String combinedPattern = String.join("|", patterns);

        String pattern = "((\\/\\+|\\/some)((\\/\\+|\\/hierarchical)((\\/\\+|\\/topic)(\\/\\+|\\/name)(\\/\\#)?))?)?\\/?\\#?";
        String pattern2 = generateRegexPattern(wildcard, 0) + "(\\/\\#)?))?)?\\/?\\#?";
        return Pattern.compile(pattern2).matcher("");
    }
}
