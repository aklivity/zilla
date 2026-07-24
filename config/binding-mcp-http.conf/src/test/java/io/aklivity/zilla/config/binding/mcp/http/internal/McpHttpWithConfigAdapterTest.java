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
package io.aklivity.zilla.config.binding.mcp.http.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.http.McpHttpWithConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class McpHttpWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpHttpWithConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
                .withProvider(YamlJson.provider())
                .withConfig(config)
                .build();
    }

    @Test
    public void shouldReadWithHeadersAndBodyTemplate()
    {
        String yaml =
                """
                headers:
                  ":method": POST
                  ":path": /repos/owner/repo/pulls
                body:
                  template:
                    title: "${args.title}"
                """;

        McpHttpWithConfig with = jsonb.fromJson(yaml, McpHttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.headers, not(nullValue()));
        assertThat(with.headers.get(":method"), equalTo("POST"));
        assertThat(with.headers.get(":path"), equalTo("/repos/owner/repo/pulls"));
        assertThat(with.body, not(nullValue()));
        assertThat(with.body.template, not(nullValue()));
        assertThat(with.body.template.get("title"), equalTo("${args.title}"));
        assertThat(with.body.model, nullValue());
        assertThat(with.query, nullValue());
    }

    @Test
    public void shouldReadWithQueryAndBody()
    {
        String yaml =
                """
                query:
                  model: test
                body:
                  model: test
                """;

        McpHttpWithConfig with = jsonb.fromJson(yaml, McpHttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.query, not(nullValue()));
        assertThat(with.body, not(nullValue()));
        assertThat(with.body.model, not(nullValue()));
        assertThat(with.body.template, nullValue());
        assertThat(with.headers, nullValue());
    }

    @Test
    public void shouldReadWithCookies()
    {
        String yaml =
                """
                headers:
                  ":method": GET
                cookies:
                  session: "${args.session.id}"
                """;

        McpHttpWithConfig with = jsonb.fromJson(yaml, McpHttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.cookies, not(nullValue()));
        assertThat(with.cookies.get("session"), equalTo("${args.session.id}"));
    }

    @Test
    public void shouldWriteWithCookies()
    {
        String yaml =
                """
                headers:
                  ":method": GET
                cookies:
                  session: "${args.session.id}"
                """;

        McpHttpWithConfig with = jsonb.fromJson(yaml, McpHttpWithConfig.class);

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, containsString("cookies:"));
        assertThat(text, containsString("session:"));
        assertThat(text, containsString("${args.session.id}"));
    }

    @Test
    public void shouldWriteWithHeadersAndBodyTemplate()
    {
        String yaml =
                """
                headers:
                  ":method": POST
                body:
                  template:
                    title: "${args.title}"
                """;

        McpHttpWithConfig with = jsonb.fromJson(yaml, McpHttpWithConfig.class);

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, containsString("headers:"));
        assertThat(text, containsString(":method"));
        assertThat(text, containsString("POST"));
        assertThat(text, containsString("body:"));
        assertThat(text, containsString("template:"));
        assertThat(text, containsString("title:"));
        assertThat(text, containsString("${args.title}"));
    }

    @Test
    public void shouldWriteWithQueryAndBody()
    {
        String yaml =
                """
                query:
                  model: test
                body:
                  model: test
                """;

        McpHttpWithConfig with = jsonb.fromJson(yaml, McpHttpWithConfig.class);

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, containsString("query:"));
        assertThat(text, containsString("body:"));
    }
}
