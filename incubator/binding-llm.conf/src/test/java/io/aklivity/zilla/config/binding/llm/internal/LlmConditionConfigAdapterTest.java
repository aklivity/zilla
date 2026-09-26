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

import io.aklivity.zilla.config.binding.llm.LlmConditionConfig;

public class LlmConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new LlmConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadConditionWithDialect()
    {
        String text =
                "{" +
                    "\"dialect\": \"openai\"" +
                "}";

        LlmConditionConfig condition = jsonb.fromJson(text, LlmConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.dialect, equalTo("openai"));
        assertThat(condition.model, nullValue());
    }

    @Test
    public void shouldWriteConditionWithDialect()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .inject(identity())
            .dialect("openai")
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"dialect\":\"openai\"}"));
    }

    @Test
    public void shouldReadConditionWithModel()
    {
        String text =
                "{" +
                    "\"model\": [ \"gpt-4o\", \"gpt-4o-mini\" ]" +
                "}";

        LlmConditionConfig condition = jsonb.fromJson(text, LlmConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.dialect, nullValue());
        assertThat(condition.model, contains("gpt-4o", "gpt-4o-mini"));
    }

    @Test
    public void shouldWriteConditionWithModel()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder()
            .model(asList("gpt-4o", "gpt-4o-mini"))
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"model\":[\"gpt-4o\",\"gpt-4o-mini\"]}"));
    }

    @Test
    public void shouldReadConditionWithDialectAndModel()
    {
        String text =
                "{" +
                    "\"dialect\": \"anthropic\"," +
                    "\"model\": [ \"claude-*\" ]" +
                "}";

        LlmConditionConfig condition = jsonb.fromJson(text, LlmConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.dialect, equalTo("anthropic"));
        assertThat(condition.model, contains("claude-*"));
    }

    @Test
    public void shouldBuildConditionViaMapper()
    {
        LlmConditionConfig condition = LlmConditionConfig.builder(LlmConditionConfig.class::cast)
            .dialect("openai")
            .build();

        assertThat(condition, not(nullValue()));
        assertThat(condition.dialect, equalTo("openai"));
    }
}
