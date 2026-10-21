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
package io.aklivity.zilla.config.binding.llm.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmModelConfig;

public class LlmModelDefaultsTest
{
    @Test
    public void shouldDefaultAnthropicModels()
    {
        List<LlmModelConfig> models = LlmModelConfig.defaults("anthropic");

        assertThat(models, not(empty()));
        assertThat(named(models, "claude-opus-4-7*").thinking, equalTo("adaptive"));
        assertThat(named(models, "claude-opus-4-7*").sampling, equalTo(false));
        assertThat(named(models, "claude-3-*").thinking, equalTo("enabled"));
    }

    @Test
    public void shouldDefaultGeminiModels()
    {
        List<LlmModelConfig> models = LlmModelConfig.defaults("gemini");

        assertThat(named(models, "gemini-2.5-pro*").thinking, equalTo("budget"));
        assertThat(named(models, "gemini-2.5-pro*").budgetMinimum, equalTo(128));
        assertThat(named(models, "gemini-2.5-pro*").budgetMaximum, equalTo(32768));
        assertThat(named(models, "gemini-2.5-flash*").budgetMinimum, equalTo(0));
    }

    @Test
    public void shouldDefaultOpenaiModels()
    {
        List<LlmModelConfig> models = LlmModelConfig.defaults("openai");

        assertThat(named(models, "o3*").sampling, equalTo(false));
        assertThat(named(models, "o3*").thinking, nullValue());
    }

    @Test
    public void shouldDefaultNothingForUnknownDialect()
    {
        assertThat(LlmModelConfig.defaults("unknown"), empty());
    }

    @Test
    public void shouldDefaultNothingWithoutDialect()
    {
        assertThat(LlmModelConfig.defaults(null), empty());
    }

    @Test
    public void shouldNameEveryDefault()
    {
        for (String dialect : List.of("openai", "anthropic", "gemini"))
        {
            for (LlmModelConfig model : LlmModelConfig.defaults(dialect))
            {
                assertThat(model.name, not(nullValue()));
            }
        }
    }

    private static LlmModelConfig named(
        List<LlmModelConfig> models,
        String name)
    {
        return models.stream()
            .filter(model -> name.equals(model.name))
            .findFirst()
            .orElse(null);
    }
}
