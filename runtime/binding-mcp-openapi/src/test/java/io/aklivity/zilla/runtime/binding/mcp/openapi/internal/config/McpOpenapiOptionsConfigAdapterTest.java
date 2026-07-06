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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import io.aklivity.zilla.runtime.binding.mcp.openapi.config.McpOpenapiOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public class McpOpenapiOptionsConfigAdapterTest
{
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ConfigAdapterContext context;

    private Jsonb jsonb;

    @Before
    public void initJson() throws IOException
    {
        OptionsConfigAdapter adapter = new OptionsConfigAdapter(OptionsConfigAdapterSpi.Kind.BINDING, context);
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
                  "description": "Create a pull request."
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
    }

    @Test
    public void shouldWriteOptions()
    {
        String expected =
            "{\"specs\":{\"openapi_github0\":{\"catalog\":{\"catalog0\":" +
            "{\"subject\":\"rest-api\",\"version\":\"latest\"}},\"server\":\"https://api.github.com\"}}," +
            "\"tools\":{\"create_pr\":{\"description\":\"Create a pull request.\"}}}";

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
                .build()
            .build();

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(expected));
    }
}
