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
package io.aklivity.zilla.config.binding.mcp.internal;

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

import io.aklivity.zilla.config.binding.mcp.McpConditionConfig;

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
        assertThat(condition.tool, nullValue());
        assertThat(condition.prompt, nullValue());
        assertThat(condition.resource, nullValue());
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
            "\"tool\":[\"create_*\",\"get_*\"],\"resource\":[\"repo://*\"]}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.toolkit, equalTo("github"));
        assertThat(condition.tool, contains("create_*", "get_*"));
        assertThat(condition.resource, contains("repo://*"));
        assertThat(condition.prompt, nullValue());
    }

    @Test
    public void shouldWriteFilterCondition()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
                .toolkit("github")
                .tool(asList("create_*", "get_*"))
                .resource(asList("repo://*"))
                .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"toolkit\":\"github\"," +
            "\"tool\":[\"create_*\",\"get_*\"],\"resource\":\"repo://*\"}"));
    }

    @Test
    public void shouldReadEmptyFilterCondition()
    {
        String text = "{\"toolkit\":\"slack\",\"tool\":[]}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, empty());
        assertThat(condition.prompt, nullValue());
        assertThat(condition.resource, nullValue());
    }

    @Test
    public void shouldWriteEmptyFilterCondition()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
                .toolkit("slack")
                .tool(emptyList())
                .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"toolkit\":\"slack\",\"tool\":[]}"));
    }

    @Test
    public void shouldReadBareStringShorthandForSingleTool()
    {
        String text = "{\"toolkit\":\"github\",\"tool\":\"get_weather\"}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, contains("get_weather"));
    }

    @Test
    public void shouldWriteSingleToolAsBareStringShorthand()
    {
        McpConditionConfig condition = McpConditionConfig.builder()
                .toolkit("github")
                .tool(asList("get_weather"))
                .build();

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"toolkit\":\"github\",\"tool\":\"get_weather\"}"));
    }

    @Test
    public void shouldReadBareStringShorthandForSinglePrompt()
    {
        String text = "{\"toolkit\":\"github\",\"prompt\":\"summarize\"}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.prompt, contains("summarize"));
    }

    @Test
    public void shouldReadBareStringShorthandForSingleResource()
    {
        String text = "{\"toolkit\":\"github\",\"resource\":\"repo://acme/widgets\"}";

        McpConditionConfig condition = jsonb.fromJson(text, McpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.resource, contains("repo://acme/widgets"));
    }
}
