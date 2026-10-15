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

import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmWithConfig;

public class LlmWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new LlmWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithFallback()
    {
        String text =
                "{" +
                    "\"fallback\": [ \"claude-opus-4-1\", \"claude-haiku-4-5\" ]" +
                "}";

        LlmWithConfig with = jsonb.fromJson(text, LlmWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.fallback, contains("claude-opus-4-1", "claude-haiku-4-5"));
    }

    @Test
    public void shouldWriteWithFallback()
    {
        LlmWithConfig with = LlmWithConfig.builder()
            .inject(identity())
            .fallback(asList("claude-opus-4-1", "claude-haiku-4-5"))
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"fallback\":[\"claude-opus-4-1\",\"claude-haiku-4-5\"]}"));
    }

    @Test
    public void shouldReadEmptyWith()
    {
        LlmWithConfig with = jsonb.fromJson("{}", LlmWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.fallback, nullValue());
    }

    @Test
    public void shouldWriteEmptyWith()
    {
        LlmWithConfig with = LlmWithConfig.builder().build();

        String text = jsonb.toJson(with);

        assertThat(text, equalTo("{}"));
    }

    @Test
    public void shouldBuildWithViaMapper()
    {
        LlmWithConfig with = LlmWithConfig.builder(LlmWithConfig.class::cast)
            .fallback(asList("claude-opus-4-1"))
            .build();

        assertThat(with.fallback, contains("claude-opus-4-1"));
    }
}
