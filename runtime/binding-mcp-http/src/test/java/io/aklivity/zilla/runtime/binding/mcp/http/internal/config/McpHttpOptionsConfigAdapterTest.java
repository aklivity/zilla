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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpResourceConfig;
import io.aklivity.zilla.runtime.binding.mcp.http.config.McpHttpToolConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class McpHttpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpHttpOptionsConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadOptions()
    {
        String yaml =
                """
                authorization:
                  github0:
                    credentials:
                      headers:
                        authorization: "Bearer ${credentials}"
                tools:
                  create_pr:
                    description: Create a pull request
                    summary: "Created pull request #${result.number}"
                    schemas:
                      input:
                        model: test
                      output:
                        model: test
                resources:
                  order:
                    uri: "order://{orderId}"
                    description: Customer order by identifier
                    mimeType: application/json
                    schemas:
                      output:
                        model: test
                """;

        McpHttpOptionsConfig options = jsonb.fromJson(yaml, McpHttpOptionsConfig.class);

        assertThat(options, not(nullValue()));

        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("github0"));
        assertThat(options.authorization.headers.get("authorization"), equalTo("Bearer ${credentials}"));

        assertThat(options.tools, hasSize(1));
        McpHttpToolConfig tool = options.tools.get(0);
        assertThat(tool.name, equalTo("create_pr"));
        assertThat(tool.description, equalTo("Create a pull request"));
        assertThat(tool.summary, equalTo("Created pull request #${result.number}"));
        assertThat(tool.input, not(nullValue()));
        assertThat(tool.output, not(nullValue()));

        assertThat(options.resources, hasSize(1));
        McpHttpResourceConfig resource = options.resources.get(0);
        assertThat(resource.name, equalTo("order"));
        assertThat(resource.uri, equalTo("order://{orderId}"));
        assertThat(resource.description, equalTo("Customer order by identifier"));
        assertThat(resource.mimeType, equalTo("application/json"));
        assertThat(resource.output, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        String yaml =
                """
                authorization:
                  github0:
                    credentials:
                      headers:
                        authorization: "Bearer ${credentials}"
                tools:
                  create_pr:
                    description: Create a pull request
                    summary: "Created pull request #${result.number}"
                    schemas:
                      input:
                        model: test
                      output:
                        model: test
                resources:
                  order:
                    uri: "order://{orderId}"
                    description: Customer order by identifier
                    mimeType: application/json
                    schemas:
                      output:
                        model: test
                """;

        McpHttpOptionsConfig options = jsonb.fromJson(yaml, McpHttpOptionsConfig.class);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, containsString("authorization:"));
        assertThat(text, containsString("github0:"));
        assertThat(text, containsString("tools:"));
        assertThat(text, containsString("create_pr:"));
        assertThat(text, containsString("summary:"));
        assertThat(text, containsString("Created pull request #${result.number}"));
        assertThat(text, containsString("input:"));
        assertThat(text, containsString("output:"));
        assertThat(text, containsString("resources:"));
        assertThat(text, containsString("order:"));
        assertThat(text, containsString("uri:"));
        assertThat(text, containsString("order://{orderId}"));
        assertThat(text, containsString("mimeType:"));
        assertThat(text, containsString("application/json"));
    }

    @Test
    public void shouldReadAndWriteMinimalOptions()
    {
        String yaml =
                """
                authorization:
                  github0: {}
                tools:
                  ping: {}
                resources:
                  ping_resource: {}
                """;

        McpHttpOptionsConfig options = jsonb.fromJson(yaml, McpHttpOptionsConfig.class);

        assertThat(options, not(nullValue()));

        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("github0"));
        assertThat(options.authorization.headers.isEmpty(), equalTo(true));

        assertThat(options.tools, hasSize(1));
        McpHttpToolConfig tool = options.tools.get(0);
        assertThat(tool.name, equalTo("ping"));
        assertThat(tool.description, nullValue());
        assertThat(tool.summary, nullValue());
        assertThat(tool.input, nullValue());
        assertThat(tool.output, nullValue());

        assertThat(options.resources, hasSize(1));
        McpHttpResourceConfig resource = options.resources.get(0);
        assertThat(resource.name, equalTo("ping_resource"));
        assertThat(resource.uri, nullValue());
        assertThat(resource.description, nullValue());
        assertThat(resource.mimeType, nullValue());
        assertThat(resource.output, nullValue());

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, containsString("github0:"));
        assertThat(text, containsString("ping:"));
        assertThat(text, containsString("ping_resource:"));
    }

    @Test
    public void shouldReadAndWriteEmptyOptions()
    {
        McpHttpOptionsConfig options = jsonb.fromJson("{}", McpHttpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.authorization, nullValue());
        assertThat(options.tools, nullValue());
        assertThat(options.resources, nullValue());

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, not(containsString("authorization")));
        assertThat(text, not(containsString("tools")));
        assertThat(text, not(containsString("resources")));
    }
}
