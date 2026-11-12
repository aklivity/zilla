/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mqtt.MqttConditionConfig;

public class MqttConditionMatcherTest
{
    @Test
    public void shouldMatchSharedAndNonSharedWhenShareOmitted()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .build()
            .build());

        assertTrue(matcher.matchesSubscribe(0L, null, "sensor/one"));
        assertTrue(matcher.matchesSubscribe(0L, "consumers", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, "consumers", "sensor/two"));
    }

    @Test
    public void shouldMatchOnlySharedWithMatchingShareName()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .build());

        assertTrue(matcher.matchesSubscribe(0L, "consumers", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, "others", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, "consumers2", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, null, "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, "consumers", "sensor/two"));
    }

    @Test
    public void shouldMatchAnySharedWithWildcardShareName()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("*")
                .build()
            .build());

        assertTrue(matcher.matchesSubscribe(0L, "consumers", "sensor/one"));
        assertTrue(matcher.matchesSubscribe(0L, "others", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, null, "sensor/one"));
    }

    @Test
    public void shouldMatchSharedWithWildcardPrefixShareName()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("ops-*")
                .build()
            .build());

        assertTrue(matcher.matchesSubscribe(0L, "ops-east", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, "dev-east", "sensor/one"));
    }

    @Test
    public void shouldMatchShareNameLiterally()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("a.b")
                .build()
            .build());

        assertTrue(matcher.matchesSubscribe(0L, "a.b", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, "axb", "sensor/one"));
    }

    @Test
    public void shouldMatchAnySubscribeCondition()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .subscribe()
                .topic("sensor/two")
                .build()
            .build());

        assertTrue(matcher.matchesSubscribe(0L, "consumers", "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, null, "sensor/one"));
        assertTrue(matcher.matchesSubscribe(0L, null, "sensor/two"));
        assertTrue(matcher.matchesSubscribe(0L, "consumers", "sensor/two"));
    }

    @Test
    public void shouldNotConstrainPublishByShare()
    {
        MqttConditionMatcher matcher = matcher(MqttConditionConfig.builder()
            .publish()
                .topic("sensor/one")
                .build()
            .build());

        assertTrue(matcher.matchesPublish(0L, "sensor/one"));
        assertFalse(matcher.matchesSubscribe(0L, null, "sensor/one"));
    }

    private static MqttConditionMatcher matcher(
        MqttConditionConfig condition)
    {
        return new MqttConditionMatcher(name -> null, name -> null, condition);
    }
}
