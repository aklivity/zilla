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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.util.List;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpKeywordToolSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.mcp.config.McpOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

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
    public void shouldReadOptionsWithAuthorizationDefaultCredentials()
    {
        String text =
                """
                authorization:
                  oauth: {}
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("oauth"));
        assertThat(options.authorization.credentials, equalTo("Bearer {credentials}"));
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

    @Test
    public void shouldReadOptionsWithCacheToolsSearchMinimal()
    {
        String text =
                """
                cache:
                  store: memory0
                  tools:
                    search:
                      toolkit: zilla
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.cache, not(nullValue()));
        assertThat(options.cache.tools, not(nullValue()));
        assertThat(options.cache.tools.search, not(nullValue()));
        assertThat(options.cache.tools.search.toolkit, equalTo("zilla"));
        assertThat(options.cache.tools.search.limit, equalTo(5));
        assertThat(options.cache.tools.search.fields, contains("name", "description"));
        assertThat(options.cache.tools.search.indexes, hasSize(1));
        assertThat(options.cache.tools.search.indexes.get(0).type, equalTo(McpKeywordToolSearchIndexConfig.NAME));
    }

    @Test
    public void shouldReadOptionsWithCacheToolsSearchWithoutToolkit()
    {
        String text =
                """
                cache:
                  store: memory0
                  tools:
                    search:
                      limit: 5
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.cache.tools.search, not(nullValue()));
        assertThat(options.cache.tools.search.toolkit, nullValue());
    }

    @Test
    public void shouldReadOptionsWithCacheToolsSearchFlatType()
    {
        String text =
                """
                cache:
                  store: memory0
                  tools:
                    search:
                      toolkit: zilla
                      type: keyword
                      limit: 10
                      fields:
                        - name
                        - description
                        - output-schema
                      weights:
                        name: 3
                        description: 1
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.cache.tools.search.limit, equalTo(10));
        assertThat(options.cache.tools.search.fields, contains("name", "description", "output-schema"));
        assertThat(options.cache.tools.search.weights.get("name"), equalTo(3.0));
        assertThat(options.cache.tools.search.weights.get("description"), equalTo(1.0));
        assertThat(options.cache.tools.search.indexes, hasSize(1));
        assertThat(options.cache.tools.search.indexes.get(0).type, equalTo(McpKeywordToolSearchIndexConfig.NAME));
    }

    @Test
    public void shouldReadOptionsWithCacheToolsSearchIndexArray()
    {
        String text =
                """
                cache:
                  store: memory0
                  tools:
                    search:
                      toolkit: zilla
                      index:
                        - type: keyword
                """;

        McpOptionsConfig options = (McpOptionsConfig) jsonb.fromJson(text, OptionsConfig.class);

        assertThat(options.cache.tools.search.indexes, hasSize(1));
        assertThat(options.cache.tools.search.indexes.get(0).type, equalTo(McpKeywordToolSearchIndexConfig.NAME));
    }

    @Test
    public void shouldWriteOptionsWithCacheToolsSearch()
    {
        McpOptionsConfig options = McpOptionsConfig.builder()
                .cache()
                    .store("memory0")
                    .tools()
                        .search()
                            .toolkit("zilla")
                            .limit(5)
                            .fields(List.of("name", "description"))
                            .index(new McpKeywordToolSearchIndexConfig())
                            .build()
                        .build()
                    .build()
                .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                """
                cache:
                  store: memory0
                  tools:
                    search:
                      toolkit: zilla
                      limit: 5
                      fields:
                        - name
                        - description
                      index:
                        - type: keyword
                """));
    }
}
