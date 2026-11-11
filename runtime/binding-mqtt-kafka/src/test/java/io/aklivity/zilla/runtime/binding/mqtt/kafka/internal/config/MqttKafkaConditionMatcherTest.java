/*
 * Copyright 2021-2026 Aklivity Inc
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.config.binding.mqtt.kafka.MqttKafkaConditionConfig;

public class MqttKafkaConditionMatcherTest
{
    @Test
    public void shouldMatchSimpleConditions()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("/some/hierarchical/topic/name")
                .build()
            .build();
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
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("/some/hierarchical/topic/name")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("/some/+"));
        assertFalse(matcher.matches("/some/hierarchical/+"));
        assertFalse(matcher.matches("/some/hierarchical/topic/name/something"));
        assertFalse(matcher.matches("some/hierarchical/topic/name"));
    }

    @Test
    public void shouldMatchSimpleConditions2()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("/some/hierarchical/topic")
                .build()
            .build();
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
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("/some/hierarchical/topic")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("/some/+"));
        assertFalse(matcher.matches("/some/something/else"));
        assertFalse(matcher.matches("/some/hierarchical/topic/name"));
        assertFalse(matcher.matches("some/hierarchical/topic"));
    }

    @Test
    public void shouldMatchWildcardConditions()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("device/#")
                .build()
            .build();
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
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("device/#")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("/device/one"));
        assertFalse(matcher.matches("devices/one"));
        assertFalse(matcher.matches("/#"));
    }

    @Test
    public void shouldMatchShareExact()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("sensor/one", "consumers"));
        assertFalse(matcher.matches("sensor/one", "consumers2"));
        assertFalse(matcher.matches("sensor/one", "other"));
        assertFalse(matcher.matches("sensor/two", "consumers"));
    }

    @Test
    public void shouldNotMatchShareConditionWhenNotShared()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertFalse(matcher.matches("sensor/one", null));
    }

    @Test
    public void shouldMatchShareWildcard()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("*")
                .build()
            .subscribe()
                .topic("sensor/two")
                .share("group-*")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("sensor/one", "consumers"));
        assertTrue(matcher.matches("sensor/two", "group-1"));
        assertFalse(matcher.matches("sensor/two", "consumers"));
        assertFalse(matcher.matches("sensor/one", null));
    }

    @Test
    public void shouldMatchSharedAndNonSharedWhenShareOmitted()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("sensor/one", null));
        assertTrue(matcher.matches("sensor/one", "consumers"));
    }

    @Test
    public void shouldMatchTopicIgnoringShare()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("sensor/one"));
    }

    @Test
    public void shouldPairEachTopicWithItsShare()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .subscribe()
                .topic("device/#")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("sensor/one", "consumers"));
        assertFalse(matcher.matches("sensor/one", "other"));
        assertTrue(matcher.matches("device/one", null));
        assertTrue(matcher.matches("device/one", "other"));
    }

    @Test
    public void shouldResolveParameterOfMatchedShareTopic()
    {
        MqttKafkaConditionConfig condition = MqttKafkaConditionConfig.builder()
            .subscribe()
                .topic("sensor/one")
                .share("consumers")
                .build()
            .subscribe()
                .topic("device/{id}")
                .share("consumers")
                .build()
            .build();
        MqttKafkaConditionMatcher matcher = new MqttKafkaConditionMatcher(condition);

        assertTrue(matcher.matches("device/one", "consumers"));
        assertEquals("one", matcher.parameter("id"));
    }
}
