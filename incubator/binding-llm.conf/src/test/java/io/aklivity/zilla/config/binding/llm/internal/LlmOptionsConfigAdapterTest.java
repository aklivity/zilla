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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.llm.LlmOptionsConfig;

public class LlmOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new LlmOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"" +
                "}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, equalTo("openai"));
    }

    @Test
    public void shouldWriteOptions()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .dialect("openai")
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"dialect\":\"openai\"}"));
    }

    @Test
    public void shouldReadOptionsWithoutDialect()
    {
        String text = "{}";

        LlmOptionsConfig options = jsonb.fromJson(text, LlmOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.dialect, nullValue());
    }

    @Test
    public void shouldWriteOptionsWithoutDialect()
    {
        LlmOptionsConfig options = LlmOptionsConfig.builder()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo("{}"));
    }
}
