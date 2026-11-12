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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidatorTest
{
    private final MqttValidator validator = new MqttValidator();

    @Test
    public void shouldValidateIsolatedMultiLevelWildcard()
    {
        assertTrue(validator.isTopicFilterValid("#"));
    }

    @Test
    public void shouldValidateMultiLevelWildcardAtEnd()
    {
        assertTrue(validator.isTopicFilterValid("/#"));
    }

    @Test
    public void shouldValidateIsolatedSingleLevelWildcard()
    {
        assertTrue(validator.isTopicFilterValid("+"));
    }

    @Test
    public void shouldValidateSingleLevelWildcardBeforeTrailingLevelSeparator()
    {
        assertTrue(validator.isTopicFilterValid("+/"));
    }

    @Test
    public void shouldValidateSingleLevelWildcardAfterLeadingLevelSeparator()
    {
        assertTrue(validator.isTopicFilterValid("/+"));
    }

    @Test
    public void shouldValidateMultiLevelWildcardAfterSingleWildcards()
    {
        assertTrue(validator.isTopicFilterValid("+/+/#"));
    }

    @Test
    public void shouldValidateIsolatedLevelSeparator()
    {
        assertTrue(validator.isTopicFilterValid("/"));
    }

    @Test
    public void shouldValidateAdjacentLevelSeparator()
    {
        assertTrue(validator.isTopicFilterValid("//"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesFilter()
    {
        assertTrue(validator.isTopicFilterValid("topic/name"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesAfterLeadingLevelSeparator()
    {
        assertTrue(validator.isTopicFilterValid("/topic/name"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesBeforeTrailingLevelSeparator()
    {
        assertTrue(validator.isTopicFilterValid("topic/name/"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesBetweenLeadingAndTrailingLevelSeparators()
    {
        assertTrue(validator.isTopicFilterValid("/topic/name/"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithAdjacentLevelSeparators()
    {
        assertTrue(validator.isTopicFilterValid("topic//name"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithLeadingAndAdjacentLevelSeparators()
    {
        assertTrue(validator.isTopicFilterValid("/topic//name"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithTrailingAndAdjacentLevelSeparators()
    {
        assertTrue(validator.isTopicFilterValid("topic//name/"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithSingleLevelWildcard()
    {
        assertTrue(validator.isTopicFilterValid("topic/+/name/"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithMultiLevelWildcard()
    {
        assertTrue(validator.isTopicFilterValid("topic/name/+"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithSingleAndMultiLevelWildcard()
    {
        assertTrue(validator.isTopicFilterValid("topic/+/name/#"));
    }

    @Test
    public void shouldValidateMultipleTopicNamesWithSingleAndMultiLevelWildcardAndAdjacentLevelSeparators()
    {
        assertTrue(validator.isTopicFilterValid("topic/+//name/#"));
    }

    @Test
    public void shouldNotValidateLeadingMultiLevelWildcard()
    {
        assertFalse(validator.isTopicFilterValid("#/"));
    }

    @Test
    public void shouldNotValidateInteriorMultiLevelWildcard()
    {
        assertFalse(validator.isTopicFilterValid("topic/#/name"));
    }

    @Test
    public void shouldNotValidateAdjacentMultiLevelWildcard()
    {
        assertFalse(validator.isTopicFilterValid("##"));
    }

    @Test
    public void shouldNotValidateAdjacentMultiLevelWildcardWithTrailingLevelSeparator()
    {
        assertFalse(validator.isTopicFilterValid("##/"));
    }

    @Test
    public void shouldNotValidateAdjacentMultiLevelWildcardWithLeadingLevelSeparator()
    {
        assertFalse(validator.isTopicFilterValid("/##"));
    }

    @Test
    public void shouldNotValidateAdjacentMultiAndSingleLevelWildcards()
    {
        assertFalse(validator.isTopicFilterValid("#+"));
    }

    @Test
    public void shouldNotValidateAdjacentSingleLevelWildcards()
    {
        assertFalse(validator.isTopicFilterValid("++"));
    }

    @Test
    public void shouldNotValidateAdjacentSingleLevelWildcardsWithTrailingLevelSeparator()
    {
        assertFalse(validator.isTopicFilterValid("++/"));
    }

    @Test
    public void shouldNotValidateAdjacentSingleLevelWildcardsWithLeadingLevelSeparator()
    {
        assertFalse(validator.isTopicFilterValid("/++"));
    }

    @Test
    public void shouldNotValidateLeadingMultiLevelWildcardsWithMultipleTopicNames()
    {
        assertFalse(validator.isTopicFilterValid("#/topic/name"));
    }

    @Test
    public void shouldNotValidateSingleLevelWildcardsCombinedWithTopicName()
    {
        assertFalse(validator.isTopicFilterValid("+topic/name"));
    }

    @Test
    public void shouldNotValidateMultiLevelWildcardsCombinedWithTopicName()
    {
        assertFalse(validator.isTopicFilterValid("#topic/name"));
    }

    @Test
    public void shouldNotValidateMultiLevelWildcardsCombinedWithTopicNameWithValidSingleLevelWildcard()
    {
        assertFalse(validator.isTopicFilterValid("#topic/+/name"));
    }

    @Test
    public void shouldNotValidateSingleLevelWildcardsCombinedWithTopicNameWithValidMultiLevelWildcard()
    {
        assertFalse(validator.isTopicFilterValid("+topic/name/#"));
    }

    @Test
    public void shouldValidateSharedTopicFilter()
    {
        assertTrue(validator.isSharedTopicFilterValid("$share/consumers/sensor/one"));
    }

    @Test
    public void shouldValidateSharedTopicFilterWithWildcards()
    {
        assertTrue(validator.isSharedTopicFilterValid("$share/consumers/+/sensor/#"));
    }

    @Test
    public void shouldNotValidateSharedTopicFilterWithEmptyShareName()
    {
        assertFalse(validator.isSharedTopicFilterValid("$share//sensor/one"));
    }

    @Test
    public void shouldNotValidateSharedTopicFilterWithWildcardShareName()
    {
        assertFalse(validator.isSharedTopicFilterValid("$share/consum+ers/sensor/one"));
        assertFalse(validator.isSharedTopicFilterValid("$share/consum#ers/sensor/one"));
    }

    @Test
    public void shouldNotValidateSharedTopicFilterWithoutTopicFilter()
    {
        assertFalse(validator.isSharedTopicFilterValid("$share/consumers"));
        assertFalse(validator.isSharedTopicFilterValid("$share/consumers/"));
    }

    @Test
    public void shouldNotValidateSharedTopicFilterWithInvalidTopicFilter()
    {
        assertFalse(validator.isSharedTopicFilterValid("$share/consumers/#/sensor"));
    }

    @Test
    public void shouldNotValidateTopicFilterWithoutSharedPrefix()
    {
        assertFalse(validator.isSharedTopicFilterValid("$share"));
        assertFalse(validator.isSharedTopicFilterValid("sensor/$share/consumers/one"));
    }

    @Test
    public void shouldRecognizeSharedTopicFilterByPrefix()
    {
        assertTrue(MqttValidator.isShared("$share/consumers/sensor/one"));
        assertFalse(MqttValidator.isShared("sensor/$share/one"));
        assertFalse(MqttValidator.isShared("$sharex/consumers/one"));
    }

    @Test
    public void shouldExtractShareNameAndPattern()
    {
        assertEquals("consumers", MqttValidator.shareName("$share/consumers/sensor/one"));
        assertEquals("sensor/one", MqttValidator.sharePattern("$share/consumers/sensor/one"));
        assertNull(MqttValidator.shareName("sensor/one"));
        assertNull(MqttValidator.sharePattern("sensor/one"));
    }

    @Test
    public void shouldComposeTopicFilter()
    {
        assertEquals("$share/consumers/sensor/one", MqttValidator.topicFilter("consumers", "sensor/one"));
        assertEquals("sensor/one", MqttValidator.topicFilter(null, "sensor/one"));
    }
}
