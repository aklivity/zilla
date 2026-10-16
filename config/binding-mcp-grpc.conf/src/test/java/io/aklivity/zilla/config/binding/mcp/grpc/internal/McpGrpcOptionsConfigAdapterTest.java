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
package io.aklivity.zilla.config.binding.mcp.grpc.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcAuthorizationConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcOptionsConfig;
import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcToolAnnotationsConfig;

public class McpGrpcOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new McpGrpcOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            """
            {
              "server": "http://localhost:8080",
              "catalog": {
                "catalog0": {
                  "subject": "sql",
                  "version": "latest"
                }
              },
              "authorization": {
                "guard0": {
                  "credentials": {
                    "headers": {
                      "authorization": "Bearer {credentials}",
                      "x-user-identity": "{identity}"
                    }
                  }
                }
              },
              "tools": {
                "example_sql_v1_sql_service_query": {
                  "title": "Query SQL",
                  "description": "Run a read-only SQL query",
                  "summary": "Ran SQL query",
                  "annotations": {
                    "readOnlyHint": true,
                    "idempotentHint": true
                  }
                },
                "example_sql_v1_sql_service_execute": {
                  "annotations": {
                    "destructiveHint": true,
                    "openWorldHint": false
                  }
                }
              }
            }
            """;

        McpGrpcOptionsConfig options = jsonb.fromJson(text, McpGrpcOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.server, equalTo("http://localhost:8080"));
        assertThat(options.catalog.name, equalTo("catalog0"));
        assertThat(options.catalog.subject, equalTo("sql"));
        assertThat(options.catalog.version, equalTo("latest"));

        McpGrpcAuthorizationConfig authorization = options.authorization;
        assertThat(authorization.name, equalTo("guard0"));
        assertThat(authorization.headers.get("authorization"), equalTo("Bearer {credentials}"));
        assertThat(authorization.headers.get("x-user-identity"), equalTo("{identity}"));

        assertThat(options.tools, hasSize(2));
        assertThat(options.tools.get(0).name, equalTo("example_sql_v1_sql_service_query"));
        assertThat(options.tools.get(0).title, equalTo("Query SQL"));
        assertThat(options.tools.get(0).description, equalTo("Run a read-only SQL query"));
        assertThat(options.tools.get(0).summary, equalTo("Ran SQL query"));

        McpGrpcToolAnnotationsConfig queryAnnotations = options.tools.get(0).annotations;
        assertThat(queryAnnotations.readOnlyHint, equalTo(true));
        assertThat(queryAnnotations.idempotentHint, equalTo(true));
        assertThat(queryAnnotations.destructiveHint, nullValue());
        assertThat(queryAnnotations.openWorldHint, nullValue());

        assertThat(options.tools.get(1).title, nullValue());
        assertThat(options.tools.get(1).summary, nullValue());
        assertThat(options.tools.get(1).annotations.destructiveHint, equalTo(true));
        assertThat(options.tools.get(1).annotations.openWorldHint, equalTo(false));
    }

    @Test
    public void shouldReadOptionsWithoutOptionalMembers()
    {
        String text =
            """
            {
              "server": "http://localhost:8080",
              "catalog": {
                "catalog0": {
                  "subject": "sql"
                }
              }
            }
            """;

        McpGrpcOptionsConfig options = jsonb.fromJson(text, McpGrpcOptionsConfig.class);

        assertThat(options.catalog.version, nullValue());
        assertThat(options.authorization, nullValue());
        assertThat(options.tools, nullValue());
    }

    @Test
    public void shouldWriteOptions()
    {
        McpGrpcOptionsConfig options = McpGrpcOptionsConfig.builder()
            .server("http://localhost:8080")
            .catalog()
                .name("catalog0")
                .subject("sql")
                .version("latest")
                .build()
            .authorization(new McpGrpcAuthorizationConfig("guard0", Map.of("authorization", "Bearer {credentials}")))
            .tool()
                .name("example_sql_v1_sql_service_query")
                .title("Query SQL")
                .summary("Ran SQL query")
                .annotations(McpGrpcToolAnnotationsConfig.builder().readOnlyHint(true).build())
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, equalTo(
            "{\"server\":\"http://localhost:8080\"," +
            "\"catalog\":{\"catalog0\":{\"subject\":\"sql\",\"version\":\"latest\"}}," +
            "\"authorization\":{\"guard0\":{\"credentials\":{\"headers\":" +
                "{\"authorization\":\"Bearer {credentials}\"}}}}," +
            "\"tools\":{\"example_sql_v1_sql_service_query\":{\"title\":\"Query SQL\"," +
                "\"summary\":\"Ran SQL query\",\"annotations\":{\"readOnlyHint\":true}}}}"));
    }
}
