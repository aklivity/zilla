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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpConditionConfig;

public class McpHttpConditionConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpHttpConditionConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadConditionWithTool()
    {
        String text =
                "{" +
                    "\"tool\": \"create_pr\"" +
                "}";

        McpHttpConditionConfig condition = jsonb.fromJson(text, McpHttpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, equalTo("create_pr"));
        assertThat(condition.resource, nullValue());
    }

    @Test
    public void shouldReadConditionWithResource()
    {
        String text =
                "{" +
                    "\"resource\": \"order\"" +
                "}";

        McpHttpConditionConfig condition = jsonb.fromJson(text, McpHttpConditionConfig.class);

        assertThat(condition, not(nullValue()));
        assertThat(condition.tool, nullValue());
        assertThat(condition.resource, equalTo("order"));
    }

    @Test
    public void shouldWriteConditionWithTool()
    {
        McpHttpConditionConfig condition = new McpHttpConditionConfig("create_pr", null);

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"tool\":\"create_pr\"}"));
    }

    @Test
    public void shouldWriteConditionWithResource()
    {
        McpHttpConditionConfig condition = new McpHttpConditionConfig(null, "order");

        String text = jsonb.toJson(condition);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"resource\":\"order\"}"));
    }
}
