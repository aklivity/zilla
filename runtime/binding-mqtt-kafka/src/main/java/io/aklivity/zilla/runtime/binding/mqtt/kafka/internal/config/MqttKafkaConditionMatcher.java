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

    private final Matcher topic;

    public MqttKafkaConditionMatcher(
        MqttKafkaConditionConfig condition)
    {
        this.topic = condition.topic != null ? asMatcher(condition.topic) : null;
    }

    public boolean matches(
        CharSequence topic)
    {
        return matchTopic(topic);
    }

    private boolean matchTopic(
        CharSequence topic)
    {
        return this.topic == null || this.topic.reset(topic).matches();
    }


    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(
                wildcard.replace(".", "\\.")
                    .replace("*", ".*")
                    .replaceAll("\\{([a-zA-Z_]+)\\}", "(?<$1>.+)"))
            .matcher("");
    }
}
