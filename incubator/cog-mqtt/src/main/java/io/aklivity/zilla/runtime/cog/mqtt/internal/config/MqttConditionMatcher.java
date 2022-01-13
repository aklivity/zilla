/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.mqtt.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.cog.mqtt.internal.types.MqttCapabilities;

public final class MqttConditionMatcher
{
    private final Matcher topicMatch;
    private final MqttCapabilities capabilitiesMatch;

    public MqttConditionMatcher(
        MqttConditionConfig condition)
    {
        this.topicMatch = condition.topic != null ? asMatcher(condition.topic) : null;
        this.capabilitiesMatch = condition.capabilities;
    }

    public boolean matches(
        String topic,
        MqttCapabilities capabilities)
    {
        return matchesTopic(topic) &&
                matchesCapabilities(capabilities);
    }

    private boolean matchesTopic(
        String topic)
    {
        return this.topicMatch == null || this.topicMatch.reset(topic).matches();
    }

    private boolean matchesCapabilities(
        MqttCapabilities capabilities)
    {
        return this.capabilitiesMatch == null || (this.capabilitiesMatch.value() & capabilities.value()) != 0;
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
