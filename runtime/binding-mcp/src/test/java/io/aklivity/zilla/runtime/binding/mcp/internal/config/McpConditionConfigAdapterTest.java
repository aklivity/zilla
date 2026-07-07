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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpConditionConfig;

public class McpConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadToolkitCondition()
    {
        String text = "{\"toolkit\":\"github\"}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.toolkit, equalTo("github"));
        assertThat(condition.tools, nullValue());
        assertThat(condition.prompts, nullValue());
        assertThat(condition.resources, nullValue());
    }

    @Test
    public void shouldWriteToolkitCondition()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
                .toolkit("github")
                .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"toolkit\":\"github\"}"));
    }

    @Test
    public void shouldReadFilterCondition()
    {
        String text = "{\"toolkit\":\"github\"," +
            "\"tools\":[\"create_*\",\"get_*\"],\"resources\":[\"repo://*\"]}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.toolkit, equalTo("github"));
        assertThat(condition.tools, contains("create_*", "get_*"));
        assertThat(condition.resources, contains("repo://*"));
        assertThat(condition.prompts, nullValue());
    }

    @Test
    public void shouldWriteFilterCondition()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
                .toolkit("github")
                .tools(asList("create_*", "get_*"))
                .resources(asList("repo://*"))
                .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"toolkit\":\"github\"," +
            "\"tools\":[\"create_*\",\"get_*\"],\"resources\":[\"repo://*\"]}"));
    }

    @Test
    public void shouldReadEmptyFilterCondition()
    {
        String text = "{\"toolkit\":\"slack\",\"tools\":[]}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tools, empty());
        assertThat(condition.prompts, nullValue());
        assertThat(condition.resources, nullValue());
    }

    @Test
    public void shouldWriteEmptyFilterCondition()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
                .toolkit("slack")
                .tools(emptyList())
                .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"toolkit\":\"slack\",\"tools\":[]}"));
    }
}
