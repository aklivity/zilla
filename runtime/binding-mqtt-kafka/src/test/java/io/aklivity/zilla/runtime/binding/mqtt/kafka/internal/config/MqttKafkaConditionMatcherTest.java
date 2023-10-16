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

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.config.MqttKafkaConditionConfig;

public class MqttKafkaConditionMatcherTest
{
    @Test
    public void shouldMatchSimpleConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig("/some/hierarchical/topic/name");
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("/some/hierarchical/topic/name"));
        assertTrue(matcher.matches("/some/hierarchical/topic/name/#"));
        assertTrue(matcher.matches("/some/hierarchical/+/name/#"));
        assertTrue(matcher.matches("/some/+/topic/+"));
        assertTrue(matcher.matches("/some/hierarchical/topic/+"));
        assertTrue(matcher.matches("/some/#"));
        assertTrue(matcher.matches("/some/hierarchical/#"));
        assertTrue(matcher.matches("#"));
        assertTrue(matcher.matches("/#"));
    }

    @Test
    public void shouldNotMatchSimpleConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig("/some/hierarchical/topic/name");
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("/some/+"));
        assertFalse(matcher.matches("/some/hierarchical/+"));
        assertFalse(matcher.matches("/some/hierarchical/topic/name/something"));
        assertFalse(matcher.matches("some/hierarchical/topic/name"));
    }

    @Test
    public void shouldMatchSimpleConditions2()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig("/some/hierarchical/topic");
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("/some/hierarchical/topic"));
        assertTrue(matcher.matches("/some/hierarchical/topic/#"));
        assertTrue(matcher.matches("/some/hierarchical/+/#"));
        assertTrue(matcher.matches("/some/+/topic"));
        assertTrue(matcher.matches("/some/+/#"));
        assertTrue(matcher.matches("/some/hierarchical/+"));
        assertTrue(matcher.matches("/some/#"));
        assertTrue(matcher.matches("#"));
        assertTrue(matcher.matches("/#"));
    }

    @Test
    public void shouldNotMatchSimpleConditions2()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig("/some/hierarchical/topic");
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("/some/+"));
        assertFalse(matcher.matches("/some/something/else"));
        assertFalse(matcher.matches("/some/hierarchical/topic/name"));
        assertFalse(matcher.matches("some/hierarchical/topic"));
    }

    @Test
    public void shouldMatchWildcardConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig("device/*");
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("device/one"));
        assertTrue(matcher.matches("device/two"));
        assertTrue(matcher.matches("device/+"));
        assertTrue(matcher.matches("device/#"));
        assertTrue(matcher.matches("device/rain/one"));
        assertTrue(matcher.matches("#"));
    }

    @Test
    public void shouldNotMatchWildcardConditions()
    {
        MqttKafkaConditionConfig condition = new MqttKafkaConditionConfig("device/*");
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("/device/one"));
        assertFalse(matcher.matches("devices/one"));
        assertFalse(matcher.matches("/#"));
    }
}
