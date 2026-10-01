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
package io.aklivity.zilla.runtime.binding.llm.internal.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmConditionConfig;

public class LlmConditionMatcherTest
{
    @Test
    public void shouldMatchDialectOnly()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .dialect("openai")
            .build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertTrue(matcher.matches("openai", null));
        assertTrue(matcher.matches("openai", "gpt-4o"));
        assertFalse(matcher.matches("anthropic", null));
    }

    @Test
    public void shouldMatchModelWithinAllowSet()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .model(asList("gpt-4o", "gpt-4o-mini"))
            .build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertTrue(matcher.matches("openai", "gpt-4o"));
        assertTrue(matcher.matches("anthropic", "gpt-4o-mini"));
        assertFalse(matcher.matches("openai", "gpt-3.5-turbo"));
    }

    @Test
    public void shouldMatchModelByGlob()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .model(asList("gpt-4o*"))
            .build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertTrue(matcher.matches("openai", "gpt-4o"));
        assertTrue(matcher.matches("openai", "gpt-4o-mini"));
        assertFalse(matcher.matches("openai", "gpt-3.5-turbo"));
    }

    @Test
    public void shouldNotMatchModelWhenNotYetKnown()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .model(asList("gpt-4o"))
            .build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertFalse(matcher.matches("openai", null));
    }

    @Test
    public void shouldNotMatchAnyModelWhenAllowSetEmpty()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .model(emptyList())
            .build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertFalse(matcher.matches("openai", "gpt-4o"));
    }

    @Test
    public void shouldMatchDialectAndModelTogether()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .dialect("openai")
            .model(asList("gpt-4o"))
            .build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertTrue(matcher.matches("openai", "gpt-4o"));
        assertFalse(matcher.matches("openai", "gpt-4o-mini"));
        assertFalse(matcher.matches("anthropic", "gpt-4o"));
    }

    @Test
    public void shouldMatchAnythingWhenUnconditioned()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder().build();
        LlmConditionMatcher matcher = new LlmConditionMatcher(condition);

        assertTrue(matcher.matches("openai", "gpt-4o"));
        assertTrue(matcher.matches("anthropic", null));
    }
}
