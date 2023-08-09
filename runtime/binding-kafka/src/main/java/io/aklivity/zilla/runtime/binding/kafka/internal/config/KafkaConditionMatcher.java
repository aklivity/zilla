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
package io.aklivity.zilla.runtime.binding.kafka.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.kafka.config.KafkaConditionConfig;

public final class KafkaConditionMatcher
{
    private final Matcher topicMatch;
    private final Matcher grouoIdMatch;

    public KafkaConditionMatcher(
        KafkaConditionConfig condition)
    {
        this.topicMatch = condition.topic != null ? asMatcher(condition.topic) : null;
        this.grouoIdMatch = condition.groupId != null ? asMatcher(condition.groupId) : null;
    }

    public boolean matches(
        String topic,
        String groupId)
    {
        return matchesTopic(topic) && matchesGroupId(groupId);
    }

    private boolean matchesTopic(
        String topic)
    {
        return this.topicMatch == null || this.topicMatch.reset(topic).matches();
    }

    private boolean matchesGroupId(
        String groupId)
    {
        return this.grouoIdMatch == null || this.grouoIdMatch.reset(groupId).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard
            .replace(".", "\\.")
            .replace("*", ".*"))
            .matcher("");
    }
}
