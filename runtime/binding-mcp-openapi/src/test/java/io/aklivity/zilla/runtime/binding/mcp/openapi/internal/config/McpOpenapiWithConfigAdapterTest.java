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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiWithConfig;

public class McpOpenapiWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new McpOpenapiWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithParamsAndBody()
    {
        String text =
            """
            {
              "spec": "openapi_github0",
              "operation": "create_pr",
              "params": {
                "owner": "${args.repository.owner}"
              },
              "body": {
                "title": "${args.title}",
                "head": "${args.pr.branch}"
              }
            }
            """;

        McpOpenapiWithConfig with = jsonb.fromJson(text, McpOpenapiWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.spec, equalTo("openapi_github0"));
        assertThat(with.operation, equalTo("create_pr"));
        assertThat(with.params, equalTo(Map.of("owner", "${args.repository.owner}")));
        assertThat(with.body, equalTo(Map.of(
            "title", "${args.title}",
            "head", "${args.pr.branch}")));
    }

    @Test
    public void shouldWriteWithParamsAndBody()
    {
        McpOpenapiWithConfig with = McpOpenapiWithConfig.builder()
            .spec("openapi_github0")
            .operation("create_pr")
            .params(Map.of("owner", "${args.repository.owner}"))
            .body(Map.of("title", "${args.title}"))
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
            "{\"spec\":\"openapi_github0\",\"operation\":\"create_pr\"," +
            "\"params\":{\"owner\":\"${args.repository.owner}\"}," +
            "\"body\":{\"title\":\"${args.title}\"}}"));
    }

    @Test
    public void shouldWriteWithNoParamsOrBody()
    {
        McpOpenapiWithConfig with = McpOpenapiWithConfig.builder()
            .spec("openapi_github0")
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"spec\":\"openapi_github0\"}"));
    }
}
