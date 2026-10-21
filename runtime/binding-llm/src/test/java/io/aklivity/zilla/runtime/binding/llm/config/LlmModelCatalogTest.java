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
package io.aklivity.zilla.runtime.binding.llm.config;

import static io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability.UNSET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmModelConfig;
import io.aklivity.zilla.config.binding.llm.LlmModelConfigBuilder;

public class LlmModelCatalogTest
{
    @Test
    public void shouldResolveUnspecifiedWithoutEntries()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(), List.of());

        assertThat(catalog.resolve("any"), sameInstance(LlmModelCapability.UNSPECIFIED));
    }

    @Test
    public void shouldResolveUnspecifiedWithoutModel()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(model("*").sampling(false).build()), List.of());

        assertThat(catalog.resolve(null), sameInstance(LlmModelCapability.UNSPECIFIED));
    }

    @Test
    public void shouldResolveUnspecifiedForModelWithoutMatch()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(model("claude-opus-4-7").sampling(false).build()), List.of());

        assertThat(catalog.resolve("claude-opus-4-6"), sameInstance(LlmModelCapability.UNSPECIFIED));
    }

    @Test
    public void shouldResolveExactModel()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("claude-opus-4-7").sampling(false).thinking("adaptive").build()), List.of());

        final LlmModelCapability capability = catalog.resolve("claude-opus-4-7");

        assertThat(capability.sampling, equalTo(false));
        assertThat(capability.thinking, equalTo(LlmThinking.ADAPTIVE));
        assertThat(capability.budgetMinimum, equalTo(UNSET));
        assertThat(capability.budgetMaximum, equalTo(UNSET));
        assertThat(capability.ladder, nullValue());
    }

    @Test
    public void shouldResolveGlobModel()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(model("claude-3-*").thinking("enabled").build()), List.of());

        assertThat(catalog.resolve("claude-3-opus-20240229").thinking, equalTo(LlmThinking.ENABLED));
        assertThat(catalog.resolve("claude-3").thinking, nullValue());
    }

    @Test
    public void shouldDefaultSamplingToAccepted()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(model("m").thinking("enabled").build()), List.of());

        assertThat(catalog.resolve("m").sampling, equalTo(true));
    }

    @Test
    public void shouldResolveBudgetRangeAndLadder()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("m")
                .budgetMinimum(128)
                .budgetMaximum(32768)
                .ladder("minimal", 100)
                .ladder("low", 1024)
                .ladder("medium", 8192)
                .ladder("high", 24576)
                .ladder("xhigh", 30000)
                .ladder("max", 32768)
                .ladder("none", 0)
                .build()),
            List.of());

        final LlmModelCapability capability = catalog.resolve("m");

        assertThat(capability.budgetMinimum, equalTo(128));
        assertThat(capability.budgetMaximum, equalTo(32768));
        assertThat(capability.ladder, equalTo(new int[] {UNSET, 100, 1024, 8192, 24576, 30000, 32768}));
    }

    @Test
    public void shouldPreferConfiguredOverDefault()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("claude-opus-4-7").sampling(true).build()),
            List.of(model("claude-opus-4-7").sampling(false).build()));

        assertThat(catalog.resolve("claude-opus-4-7").sampling, equalTo(true));
    }

    @Test
    public void shouldMergeConfiguredAndDefaultSettingsPerField()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("claude-opus-4-7").sampling(true).build()),
            List.of(model("claude-opus-4-7*").sampling(false).thinking("adaptive").build()));

        final LlmModelCapability capability = catalog.resolve("claude-opus-4-7");

        assertThat(capability.sampling, equalTo(true));
        assertThat(capability.thinking, equalTo(LlmThinking.ADAPTIVE));
    }

    @Test
    public void shouldPreferExactOverGlobWithinSource()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("claude-*").thinking("enabled").build(), model("claude-opus").thinking("adaptive").build()),
            List.of());

        assertThat(catalog.resolve("claude-opus").thinking, equalTo(LlmThinking.ADAPTIVE));
        assertThat(catalog.resolve("claude-haiku").thinking, equalTo(LlmThinking.ENABLED));
    }

    @Test
    public void shouldPreferFirstGlobWithinSource()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("claude-opus-*").thinking("adaptive").build(), model("claude-*").thinking("enabled").build()),
            List.of());

        assertThat(catalog.resolve("claude-opus-4-7").thinking, equalTo(LlmThinking.ADAPTIVE));
    }

    @Test
    public void shouldResolveFirstNonEmptyLadder()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("m").ladder("low", 500).build()),
            List.of(model("m").ladder("low", 1000).ladder("high", 2000).build()));

        assertThat(catalog.resolve("m").ladder, equalTo(new int[] {UNSET, UNSET, 500, UNSET, UNSET, UNSET, UNSET}));
    }

    @Test
    public void shouldResolveDefaultsOfDialect()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(), LlmModelConfig.defaults("anthropic"));

        assertThat(catalog.resolve("claude-opus-4-7-20260301").thinking, equalTo(LlmThinking.ADAPTIVE));
        assertThat(catalog.resolve("claude-opus-4-7-20260301").sampling, equalTo(false));
        assertThat(catalog.resolve("claude-3-opus-20240229").thinking, equalTo(LlmThinking.ENABLED));
        assertThat(catalog.resolve("claude-sonnet-4-6").thinking, equalTo(LlmThinking.ADAPTIVE_ENABLED));
        assertThat(catalog.resolve("unlisted-model"), notNullValue());
        assertThat(catalog.resolve("unlisted-model"), sameInstance(LlmModelCapability.UNSPECIFIED));
    }

    @Test
    public void shouldOverrideDefaultsOfDialect()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(
            List.of(model("claude-opus-4-7*").sampling(true).thinking("adaptive-disabled").build()),
            LlmModelConfig.defaults("anthropic"));

        assertThat(catalog.resolve("claude-opus-4-7").sampling, equalTo(true));
        assertThat(catalog.resolve("claude-opus-4-7").thinking, equalTo(LlmThinking.ADAPTIVE_DISABLED));
    }

    @Test
    public void shouldResolveGeminiDefaults()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(), LlmModelConfig.defaults("gemini"));

        final LlmModelCapability pro = catalog.resolve("gemini-2.5-pro");
        final LlmModelCapability flash = catalog.resolve("gemini-2.5-flash");

        assertThat(pro.thinking, equalTo(LlmThinking.BUDGET));
        assertThat(pro.budgetMinimum, equalTo(128));
        assertThat(pro.budgetMaximum, equalTo(32768));
        assertThat(flash.budgetMinimum, equalTo(0));
        assertThat(flash.budgetMaximum, equalTo(24576));
        assertThat(catalog.resolve("gemini-3.6-flash"), sameInstance(LlmModelCapability.UNSPECIFIED));
    }

    @Test
    public void shouldResolveOpenaiDefaults()
    {
        final LlmModelCatalog catalog = new LlmModelCatalog(List.of(), LlmModelConfig.defaults("openai"));

        assertThat(catalog.resolve("o3-mini").sampling, equalTo(false));
        assertThat(catalog.resolve("gpt-4o"), sameInstance(LlmModelCapability.UNSPECIFIED));
    }

    private static LlmModelConfigBuilder<LlmModelConfig> model(
        String name)
    {
        return LlmModelConfig.builder().name(name);
    }
}
