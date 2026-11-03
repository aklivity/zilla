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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier;

public class LlmServiceTiersTest
{
    private static final LlmModelCapability DEFAULT_ONLY = capability("default");
    private static final LlmModelCapability NONE = capability();

    @Test
    public void shouldDecodeOpenaiTiers()
    {
        assertThat(LlmServiceTiers.canonicalOpenai("auto"), equalTo("auto"));
        assertThat(LlmServiceTiers.canonicalOpenai("default"), equalTo("default"));
        assertThat(LlmServiceTiers.canonicalOpenai("flex"), equalTo("flex"));
        assertThat(LlmServiceTiers.canonicalOpenai("priority"), equalTo("priority"));
    }

    @Test
    public void shouldKeepOpenaiTiersThatAreNotCanonicalPrefixed()
    {
        assertThat(LlmServiceTiers.canonicalOpenai("scale"), nullValue());
        assertThat(LlmServiceTiers.canonicalOpenai("fast"), nullValue());
        assertThat(LlmServiceTiers.canonicalOpenai("AUTO"), nullValue());
        assertThat(LlmServiceTiers.canonicalOpenai(null), nullValue());
    }

    @Test
    public void shouldDecodeAnthropicTiers()
    {
        assertThat(LlmServiceTiers.canonicalAnthropic("auto"), equalTo("auto"));
        assertThat(LlmServiceTiers.canonicalAnthropic("standard_only"), equalTo("default"));
        assertThat(LlmServiceTiers.canonicalAnthropic("default"), nullValue());
        assertThat(LlmServiceTiers.canonicalAnthropic(null), nullValue());
    }

    @Test
    public void shouldDecodeGeminiTiersIgnoringCase()
    {
        assertThat(LlmServiceTiers.canonicalGemini("standard"), equalTo("default"));
        assertThat(LlmServiceTiers.canonicalGemini("STANDARD"), equalTo("default"));
        assertThat(LlmServiceTiers.canonicalGemini("flex"), equalTo("flex"));
        assertThat(LlmServiceTiers.canonicalGemini("Priority"), equalTo("priority"));
        assertThat(LlmServiceTiers.canonicalGemini("unspecified"), nullValue());
        assertThat(LlmServiceTiers.canonicalGemini(null), nullValue());
    }

    @Test
    public void shouldEncodeOpenaiTiers()
    {
        assertThat(LlmServiceTiers.openai(LlmModelCapability.UNSPECIFIED, "auto"), equalTo("auto"));
        assertThat(LlmServiceTiers.openai(LlmModelCapability.UNSPECIFIED, "default"), equalTo("default"));
        assertThat(LlmServiceTiers.openai(LlmModelCapability.UNSPECIFIED, "flex"), equalTo("flex"));
        assertThat(LlmServiceTiers.openai(LlmModelCapability.UNSPECIFIED, "priority"), equalTo("priority"));
        assertThat(LlmServiceTiers.openai(LlmModelCapability.UNSPECIFIED, "scale"), nullValue());
    }

    @Test
    public void shouldEncodeAnthropicTiers()
    {
        assertThat(LlmServiceTiers.anthropic(LlmModelCapability.UNSPECIFIED, "auto"), equalTo("auto"));
        assertThat(LlmServiceTiers.anthropic(LlmModelCapability.UNSPECIFIED, "default"), equalTo("standard_only"));
        assertThat(LlmServiceTiers.anthropic(LlmModelCapability.UNSPECIFIED, "flex"), nullValue());
        assertThat(LlmServiceTiers.anthropic(LlmModelCapability.UNSPECIFIED, "priority"), nullValue());
        assertThat(LlmServiceTiers.anthropic(LlmModelCapability.UNSPECIFIED, "standard_only"), nullValue());
    }

    @Test
    public void shouldEncodeGeminiTiers()
    {
        assertThat(LlmServiceTiers.gemini(LlmModelCapability.UNSPECIFIED, "default"), equalTo("standard"));
        assertThat(LlmServiceTiers.gemini(LlmModelCapability.UNSPECIFIED, "flex"), equalTo("flex"));
        assertThat(LlmServiceTiers.gemini(LlmModelCapability.UNSPECIFIED, "priority"), equalTo("priority"));
        assertThat(LlmServiceTiers.gemini(LlmModelCapability.UNSPECIFIED, "auto"), nullValue());
        assertThat(LlmServiceTiers.gemini(LlmModelCapability.UNSPECIFIED, "standard"), nullValue());
    }

    @Test
    public void shouldDropTiersTheModelDoesNotSupport()
    {
        assertThat(LlmServiceTiers.openai(DEFAULT_ONLY, "default"), equalTo("default"));
        assertThat(LlmServiceTiers.openai(DEFAULT_ONLY, "flex"), nullValue());
        assertThat(LlmServiceTiers.anthropic(DEFAULT_ONLY, "default"), equalTo("standard_only"));
        assertThat(LlmServiceTiers.anthropic(DEFAULT_ONLY, "auto"), nullValue());
        assertThat(LlmServiceTiers.gemini(DEFAULT_ONLY, "default"), equalTo("standard"));
        assertThat(LlmServiceTiers.gemini(DEFAULT_ONLY, "priority"), nullValue());
    }

    @Test
    public void shouldDropEveryTierWhenModelSupportsNone()
    {
        for (String tier : new String[] {"auto", "default", "flex", "priority"})
        {
            assertThat(LlmServiceTiers.openai(NONE, tier), nullValue());
            assertThat(LlmServiceTiers.anthropic(NONE, tier), nullValue());
            assertThat(LlmServiceTiers.gemini(NONE, tier), nullValue());
        }
    }

    private static LlmModelCapability capability(
        String... tiers)
    {
        return new LlmModelCapability(true, null, -1, -1, null, LlmServiceTier.maskOf(List.of(tiers)));
    }
}
