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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaSubscribeConfig;

public class MqttKafkaConditionMatcherTest
{
    @Test
    public void shouldMatchSimpleConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(
            List.of(new MqttKafkaSubscribeConfig("/some/hierarchical/topic/name")), Collections.emptyList());
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesSubscribe("/some/hierarchical/topic/name"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/topic/name/#"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/+/name/#"));
        assertTrue(matcher.matchesSubscribe("/some/+/topic/+"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/topic/+"));
        assertTrue(matcher.matchesSubscribe("/some/#"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/#"));
        assertTrue(matcher.matchesSubscribe("#"));
        assertTrue(matcher.matchesSubscribe("/#"));
    }

    @Test
    public void shouldNotMatchSimpleConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(
            List.of(new MqttKafkaSubscribeConfig("/some/hierarchical/topic/name")), Collections.emptyList());
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matchesSubscribe("/some/+"));
        assertFalse(matcher.matchesSubscribe("/some/hierarchical/+"));
        assertFalse(matcher.matchesSubscribe("/some/hierarchical/topic/name/something"));
        assertFalse(matcher.matchesSubscribe("some/hierarchical/topic/name"));
    }

    @Test
    public void shouldMatchSimpleConditions2()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(
            List.of(new MqttKafkaSubscribeConfig("/some/hierarchical/topic")), Collections.emptyList());
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesSubscribe("/some/hierarchical/topic"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/topic/#"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/+/#"));
        assertTrue(matcher.matchesSubscribe("/some/+/topic"));
        assertTrue(matcher.matchesSubscribe("/some/+/#"));
        assertTrue(matcher.matchesSubscribe("/some/hierarchical/+"));
        assertTrue(matcher.matchesSubscribe("/some/#"));
        assertTrue(matcher.matchesSubscribe("#"));
        assertTrue(matcher.matchesSubscribe("/#"));
    }

    @Test
    public void shouldNotMatchSimpleConditions2()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(
            List.of(new MqttKafkaSubscribeConfig("/some/hierarchical/topic")), Collections.emptyList());
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matchesSubscribe("/some/+"));
        assertFalse(matcher.matchesSubscribe("/some/something/else"));
        assertFalse(matcher.matchesSubscribe("/some/hierarchical/topic/name"));
        assertFalse(matcher.matchesSubscribe("some/hierarchical/topic"));
    }

    @Test
    public void shouldMatchWildcardConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(
            List.of(new MqttKafkaSubscribeConfig("device/#")), Collections.emptyList());
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matchesSubscribe("device/one"));
        assertTrue(matcher.matchesSubscribe("device/two"));
        assertTrue(matcher.matchesSubscribe("device/+"));
        assertTrue(matcher.matchesSubscribe("device/#"));
        assertTrue(matcher.matchesSubscribe("device/rain/one"));
        assertTrue(matcher.matchesSubscribe("#"));
    }

    @Test
    public void shouldNotMatchWildcardConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig(
            List.of(new MqttKafkaSubscribeConfig("device/#")), Collections.emptyList());
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matchesSubscribe("/device/one"));
        assertFalse(matcher.matchesSubscribe("devices/one"));
        assertFalse(matcher.matchesSubscribe("/#"));
    }
}
