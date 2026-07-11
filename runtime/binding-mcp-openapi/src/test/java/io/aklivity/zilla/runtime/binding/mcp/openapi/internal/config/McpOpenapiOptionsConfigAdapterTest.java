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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;
import io.aklivity.zilla.runtime.model.core.config.StringModelConfig;

public class McpOpenapiOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING);
        adapter.adaptType("mcp_openapi");
        JsonbConfig config = new JsonbConfig()
            .withAdapters(adapter);
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
            """
            {
              "specs": {
                "openapi_github0": {
                  "server": "https://api.github.com",
                  "catalog": {
                    "catalog0": {
                      "subject": "rest-api",
                      "version": "latest"
                    }
                  }
                }
              },
              "tools": {
                "create_pr": {
                  "description": "Create a pull request.",
                  "summary": "Create a pull request",
                  "input": {
                    "model": "string"
                  }
                }
              },
              "resources": {
                "repo://{owner}/{repo}": {
                  "description": "A GitHub repository.",
                  "output": {
                    "model": "string"
                  }
                }
              }
            }
            """;

        McpOpenapiOptionsConfig options = jsonb.fromJson(text, McpOpenapiOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.specs, not(nullValue()));
        assertThat(options.specs.size(), equalTo(1));
        assertThat(options.specs.get(0).label, equalTo("openapi_github0"));
        assertThat(options.specs.get(0).server, equalTo("https://api.github.com"));
        assertThat(options.specs.get(0).catalogs.get(0).name, equalTo("catalog0"));
        assertThat(options.specs.get(0).catalogs.get(0).subject, equalTo("rest-api"));
        assertThat(options.tools, not(nullValue()));
        assertThat(options.tools.get(0).name, equalTo("create_pr"));
        assertThat(options.tools.get(0).description, equalTo("Create a pull request."));
        assertThat(options.tools.get(0).summary, equalTo("Create a pull request"));
        assertThat(options.tools.get(0).input, not(nullValue()));
        assertThat(options.resources, not(nullValue()));
        assertThat(options.resources.get(0).uri, equalTo("repo://{owner}/{repo}"));
        assertThat(options.resources.get(0).description, equalTo("A GitHub repository."));
        assertThat(options.resources.get(0).output, not(nullValue()));
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected =
            "{\"specs\":{\"openapi_github0\":{\"catalog\":{\"catalog0\":" +
            "{\"subject\":\"rest-api\",\"version\":\"latest\"}},\"server\":\"https://api.github.com\"}}," +
            "\"tools\":{\"create_pr\":{\"description\":\"Create a pull request.\"," +
            "\"summary\":\"Create a pull request\",\"input\":\"string\"}}," +
            "\"resources\":{\"repo://{owner}/{repo}\":" +
            "{\"description\":\"A GitHub repository.\",\"output\":\"string\"}}}";

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec()
                .label("openapi_github0")
                .server("https://api.github.com")
                .catalog()
                    .name("catalog0")
                    .subject("rest-api")
                    .version("latest")
                    .build()
                .build()
            .tool()
                .name("create_pr")
                .description("Create a pull request.")
                .summary("Create a pull request")
                .input(StringModelConfig.builder().build())
                .build()
            .resource()
                .uri("repo://{owner}/{repo}")
                .description("A GitHub repository.")
                .output(StringModelConfig.builder().build())
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }

    @Test
    public void shouldReadOptionsWithResourceMimeType()
    {
        String text =
            """
            {
              "resources": {
                "repo://{owner}/{repo}": {
                  "description": "A GitHub repository.",
                  "mimeType": "application/vnd.github+json",
                  "output": {
                    "model": "string"
                  }
                }
              }
            }
            """;

        McpOpenapiOptionsConfig options = jsonb.fromJson(text, McpOpenapiOptionsConfig.class);

        assertThat(options.resources, not(nullValue()));
        assertThat(options.resources.get(0).mimeType, equalTo("application/vnd.github+json"));
    }

    @Test
    public void shouldWriteOptionsWithResourceMimeType()
    {
        String expected =
            "{\"resources\":{\"repo://{owner}/{repo}\":" +
            "{\"description\":\"A GitHub repository.\",\"mimeType\":\"application/vnd.github+json\"," +
            "\"output\":\"string\"}}}";

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .resource()
                .uri("repo://{owner}/{repo}")
                .description("A GitHub repository.")
                .mimeType("application/vnd.github+json")
                .output(StringModelConfig.builder().build())
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }

    @Test
    public void shouldReadOptionsWithAuthorization()
    {
        String text =
            """
            {
              "authorization": {
                "guard0": {
                  "credentials": {
                    "headers": {
                      "authorization": "Bearer {credentials}"
                    }
                  }
                }
              }
            }
            """;

        McpOpenapiOptionsConfig options = jsonb.fromJson(text, McpOpenapiOptionsConfig.class);

        assertThat(options.authorization, not(nullValue()));
        assertThat(options.authorization.name, equalTo("guard0"));
        assertThat(options.authorization.headers.get("authorization"), equalTo("Bearer {credentials}"));
    }

    @Test
    public void shouldWriteOptionsWithAuthorization()
    {
        String expected =
            "{\"authorization\":{\"guard0\":{\"credentials\":{\"headers\":{\"authorization\":\"Bearer {credentials}\"}}}}}";

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .authorization(new McpOpenapiAuthorizationConfig("guard0", Map.of("authorization", "Bearer {credentials}")))
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }

    @Test
    public void shouldReadOptionsWithOverlay()
    {
        String text =
            """
            {
              "specs": {
                "openapi_github0": {
                  "catalog": {
                    "catalog0": {
                      "subject": "rest-api",
                      "version": "latest"
                    }
                  },
                  "overlay": {
                    "overlay0": {
                      "subject": "rest-api-overlay",
                      "version": "latest"
                    }
                  }
                }
              }
            }
            """;

        McpOpenapiOptionsConfig options = jsonb.fromJson(text, McpOpenapiOptionsConfig.class);

        McpOpenapiSpecificationConfig spec = options.specs.get(0);
        assertThat(spec.overlay, not(nullValue()));
        assertThat(spec.overlay.name, equalTo("overlay0"));
        assertThat(spec.overlay.subject, equalTo("rest-api-overlay"));
    }

    @Test
    public void shouldWriteOptionsWithOverlay()
    {
        String expected =
            "{\"specs\":{\"openapi_github0\":{\"catalog\":{\"catalog0\":" +
            "{\"subject\":\"rest-api\",\"version\":\"latest\"}}," +
            "\"overlay\":{\"overlay0\":{\"subject\":\"rest-api-overlay\",\"version\":\"latest\"}}}}}";

        McpOpenapiOptionsConfig options = McpOpenapiOptionsConfig.builder()
            .spec()
                .label("openapi_github0")
                .catalog()
                    .name("catalog0")
                    .subject("rest-api")
                    .version("latest")
                    .build()
                .overlay()
                    .name("overlay0")
                    .subject("rest-api-overlay")
                    .version("latest")
                    .build()
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }
}
