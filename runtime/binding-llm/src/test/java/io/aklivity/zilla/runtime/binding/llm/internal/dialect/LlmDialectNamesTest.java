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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmDialectNamesTest
{
    @Test
    public void shouldNameVendorOfDialectVariant()
    {
        assertEquals("openai", LlmDialectNames.vendor("openai/responses"));
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
        assertTrue(LlmDialectNames.admits("openai/responses", "openai/responses"));
    }

    @Test
    public void shouldAdmitEveryVariantOfVendor()
    {
        assertTrue(LlmDialectNames.admits("openai", "openai/responses"));
        assertTrue(LlmDialectNames.admits("anthropic", "anthropic/messages"));
    }

    @Test
    public void shouldAdmitOnlyNamedVariant()
    {
        assertFalse(LlmDialectNames.admits("openai/responses", "openai"));
        assertFalse(LlmDialectNames.admits("openai/responses", "openai/completions"));
        assertFalse(LlmDialectNames.admits("openai/responses", "openai/responses/v2"));
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
}
