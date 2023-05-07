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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MqttKafkaConditionMatcher
{
    private final Matcher topicMatch;

    public MqttKafkaConditionMatcher(
        MqttKafkaConditionConfig condition)
    {
        this.topicMatch = condition.topic != null ? asMatcher(condition.topic) : null;
    }

    public boolean matches(
        String topic)
    {
        return matchesTopic(topic);
    }

    private boolean matchesTopic(
        String topic)
    {
        return this.topicMatch == null || this.topicMatch.reset(topic).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard
            .replace(".", "\\.")
            .replace("$", "\\$")
            .replace("+", "[^/]*")
            .replace("#", ".*")).matcher("");
    }
}
