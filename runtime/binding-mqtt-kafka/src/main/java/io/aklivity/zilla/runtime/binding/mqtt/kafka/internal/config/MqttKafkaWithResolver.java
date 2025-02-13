/*
 * Copyright 2021-2024 Aklivity Inc
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

import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaOptionsConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaWithConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.String16FW;

public class MqttKafkaWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");

    private final Matcher paramsMatcher;
    private final MqttKafkaWithConfig with;
    private final MqttKafkaOptionsConfig options;

    private Function<MatchResult, String> replacer = r -> null;

    public MqttKafkaWithResolver(
        MqttKafkaOptionsConfig options,
        MqttKafkaWithConfig with)
    {
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.with = with;
        this.options = options;
    }

    public void onConditionMatched(
        MqttKafkaConditionMatcher condition)
    {
        this.replacer = r -> condition.parameter(r.group(1));
    }

    public boolean containsParams()
    {
        return with != null && paramsMatcher.reset(with.messages).find();
    }

    public String16FW resolveMessages()
    {
        String topic = null;
        if (with != null)
        {
            topic = with.messages;
            Matcher topicMatcher = paramsMatcher.reset(topic);
            StringBuilder result = new StringBuilder();
            while (topicMatcher.find())
            {
                String replacement = replacer.apply(paramsMatcher.toMatchResult());
                topicMatcher.appendReplacement(result, replacement);
            }
            topicMatcher.appendTail(result);

            topic = result.toString();
        }
        return topic == null ? options.topics.messages : new String16FW(topic);
    }

    public long compositeId()
    {
        return with.compositeId;
    }
}
