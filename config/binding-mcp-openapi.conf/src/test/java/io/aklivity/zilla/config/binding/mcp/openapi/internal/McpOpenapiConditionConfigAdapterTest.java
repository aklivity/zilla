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
package io.aklivity.zilla.config.binding.mcp.openapi.internal;

import static java.util.List.of;
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

import io.aklivity.zilla.config.binding.mcp.openapi.McpOpenapiConditionConfig;

public class McpOpenapiConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpOpenapiConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadConditionWithTool()
    {
        String text =
                "{" +
                    "\"tool\": \"create_pr\"" +
                "}";

        McpOpenapiConditionConfig condition = jsonb.fromJson(text, McpOpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, equalTo("create_pr"));
        assertThat(condition.resource, nullValue());
        assertThat(condition.capability, nullValue());
    }

    @Test
    public void shouldReadConditionWithResource()
    {
        String text =
                "{" +
                    "\"resource\": \"repo://{owner}/{repo}\"" +
                "}";

        McpOpenapiConditionConfig condition = jsonb.fromJson(text, McpOpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, nullValue());
        assertThat(condition.resource, equalTo("repo://{owner}/{repo}"));
    }

    @Test
    public void shouldReadConditionWithCapability()
    {
        String text =
                "{" +
                    "\"capability\": [\"fetch\", \"subscribe\"]" +
                "}";

        McpOpenapiConditionConfig condition = jsonb.fromJson(text, McpOpenapiConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.capability, contains("fetch", "subscribe"));
    }

    @Test
    public void shouldWriteConditionWithTool()
    {
        McpOpenapiConditionConfig condition = McpOpenapiConditionConfig.builder()
            .tool("create_pr")
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"tool\":\"create_pr\"}"));
    }

    @Test
    public void shouldWriteConditionWithResource()
    {
        McpOpenapiConditionConfig condition = McpOpenapiConditionConfig.builder()
            .resource("repo://{owner}/{repo}")
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"resource\":\"repo://{owner}/{repo}\"}"));
    }

    @Test
    public void shouldWriteConditionWithCapability()
    {
        McpOpenapiConditionConfig condition = McpOpenapiConditionConfig.builder()
            .capability(of("fetch", "subscribe"))
            .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"capability\":[\"fetch\",\"subscribe\"]}"));
    }
}
