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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValueValidatorTest
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
}
