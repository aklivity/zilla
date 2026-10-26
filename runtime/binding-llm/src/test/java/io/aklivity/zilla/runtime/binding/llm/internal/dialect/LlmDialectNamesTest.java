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
package io.aklivity.zilla.runtime.binding.llm.internal.dialect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmDialectNamesTest
{
    @Test
    public void shouldNameVendorOfDialectVariant()
    {
        assertEquals("openai", LlmDialectNames.vendor("openai/rs"));
        assertEquals("openai", LlmDialectNames.vendor("openai/ccs"));
    }

    @Test
    public void shouldNameVendorOfDialectWithoutVariant()
    {
        assertEquals("openai", LlmDialectNames.vendor("openai"));
        assertEquals("anthropic", LlmDialectNames.vendor("anthropic"));
    }

    @Test
    public void shouldAdmitDialectNamedExactly()
    {
        assertTrue(LlmDialectNames.admits("openai", "openai"));
        assertTrue(LlmDialectNames.admits("openai/rs", "openai/rs"));
        assertTrue(LlmDialectNames.admits("openai/ccs", "openai/ccs"));
    }

    @Test
    public void shouldAdmitEveryVariantOfVendor()
    {
        assertTrue(LlmDialectNames.admits("openai", "openai/rs"));
        assertTrue(LlmDialectNames.admits("openai", "openai/ccs"));
        assertTrue(LlmDialectNames.admits("anthropic", "anthropic/messages"));
    }

    @Test
    public void shouldAdmitOnlyNamedVariant()
    {
        assertFalse(LlmDialectNames.admits("openai/rs", "openai"));
        assertFalse(LlmDialectNames.admits("openai/rs", "openai/ccs"));
        assertFalse(LlmDialectNames.admits("openai/ccs", "openai/rs"));
        assertFalse(LlmDialectNames.admits("openai/rs", "openai/rs/v2"));
    }

    @Test
    public void shouldNotAdmitOtherVendor()
    {
        assertFalse(LlmDialectNames.admits("openai", "anthropic"));
        assertFalse(LlmDialectNames.admits("openai", "openai-compatible"));
        assertFalse(LlmDialectNames.admits("openai", "openaix/responses"));
    }

    @Test
    public void shouldNotAdmitMissingDialect()
    {
        assertFalse(LlmDialectNames.admits("openai", null));
    }

    @Test
    public void shouldNotAdmitMissingPattern()
    {
        assertFalse(LlmDialectNames.admits((String) null, "openai/ccs"));
        assertFalse(LlmDialectNames.admits((CharSequence) null, 0, 0, "openai/ccs"));
    }

    @Test
    public void shouldAdmitPatternRegion()
    {
        final String key = "openai/rs:store";

        assertTrue(LlmDialectNames.admits(key, 0, 9, "openai/rs"));
        assertTrue(LlmDialectNames.admits(key, 0, 6, "openai/rs"));
        assertTrue(LlmDialectNames.admits("openai:store", 0, 6, "openai/ccs"));
        assertTrue(LlmDialectNames.admits("openai:store", 0, 6, "openai"));
    }

    @Test
    public void shouldNotAdmitPatternRegionOfOtherDialect()
    {
        final String key = "openai/rs:store";

        assertFalse(LlmDialectNames.admits(key, 0, 9, "openai/ccs"));
        assertFalse(LlmDialectNames.admits(key, 0, 9, "openai"));
        assertFalse(LlmDialectNames.admits(key, 0, 9, "anthropic"));
        assertFalse(LlmDialectNames.admits("openai:store", 0, 6, "anthropic"));
        assertFalse(LlmDialectNames.admits("openai:store", 0, 6, null));
    }

    @Test
    public void shouldNameApiOfDialect()
    {
        assertEquals("chat/completions", LlmDialectNames.api("openai/ccs"));
        assertEquals("responses", LlmDialectNames.api("openai/rs"));
    }

    @Test
    public void shouldNotNameApiOfDialectWithoutApis()
    {
        assertNull(LlmDialectNames.api("anthropic"));
        assertNull(LlmDialectNames.api("gemini"));
        assertNull(LlmDialectNames.api("openai"));
        assertNull(LlmDialectNames.api(null));
    }

    @Test
    public void shouldResolveDialectOfApi()
    {
        assertEquals("openai/ccs", LlmDialectNames.id("openai", "chat/completions"));
        assertEquals("openai/rs", LlmDialectNames.id("openai", "responses"));
    }

    @Test
    public void shouldNotResolveDialectOfUnknownApi()
    {
        assertNull(LlmDialectNames.id("openai", "embeddings"));
        assertNull(LlmDialectNames.id("anthropic", "responses"));
    }

    @Test
    public void shouldDefaultVendorToChatCompletions()
    {
        assertEquals("openai/ccs", LlmDialectNames.defaultOf("openai"));
    }

    @Test
    public void shouldDefaultDialectToItself()
    {
        assertEquals("openai/rs", LlmDialectNames.defaultOf("openai/rs"));
        assertEquals("openai/ccs", LlmDialectNames.defaultOf("openai/ccs"));
        assertEquals("anthropic", LlmDialectNames.defaultOf("anthropic"));
        assertEquals("gemini", LlmDialectNames.defaultOf("gemini"));
    }
}
