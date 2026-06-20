/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;

public class McpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptionsWithElicitationTimeout()
    {
        String text =
                """
                elicitation:
                  timeout: PT30S
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.elicitation, not(nullValue()));
        assertThat(options.elicitation.timeout, equalTo(Duration.ofSeconds(30)));
    }

    @Test
    public void shouldWriteOptionsWithElicitationTimeout()
    {
        McpOptionsConfig options = McpOptionsConfig.builder()
                .elicitation()
                    .timeout(Duration.ofSeconds(30))
                    .build()
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                """
                elicitation:
                  callback: auth/callback
                  timeout: PT30S
                """));
    }

    @Test
    public void shouldReadOptionsWithServer()
    {
        String text =
                """
                server: http://localhost:8080/mcp
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server, equalTo("http://localhost:8080/mcp"));
    }

    @Test
    public void shouldWriteOptionsWithServer()
    {
        McpOptionsConfig options = McpOptionsConfig.builder()
                .server("http://localhost:8080/mcp")
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                """
                server: "http://localhost:8080/mcp"
                """));
    }
}
