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
package io.aklivity.zilla.config.binding.a2a.http.internal;

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

import io.aklivity.zilla.config.binding.a2a.http.A2aHttpWithConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class A2aHttpWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aHttpWithConfigAdapter());
        jsonb = JsonbBuilder.newBuilder()
            .withProvider(YamlJson.provider())
            .withConfig(config)
            .build();
    }

    @Test
    public void shouldReadWithHeaders()
    {
        String yaml =
            """
            headers:
              ":method": GET
              ":path": "/items/${args.id}"
            """;

        A2aHttpWithConfig with = jsonb.fromJson(yaml, A2aHttpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.headers.get(":method"), equalTo("GET"));
        assertThat(with.headers.get(":path"), equalTo("/items/${args.id}"));
        assertThat(with.body, nullValue());
    }

    @Test
    public void shouldReadWithHeadersAndBodyModel()
    {
        String yaml =
            """
            headers:
              ":method": POST
            body:
              model: test
            """;

        A2aHttpWithConfig with = jsonb.fromJson(yaml, A2aHttpWithConfig.class);

        assertThat(with.headers.get(":method"), equalTo("POST"));
        assertThat(with.body, not(nullValue()));
    }

    @Test
    public void shouldWriteWithHeaders()
    {
        A2aHttpWithConfig with = A2aHttpWithConfig.builder()
            .header(":method", "GET")
            .header(":path", "/items/${args.id}")
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, containsString("\":method\": GET"));
        assertThat(text, containsString("${args.id}"));
        assertThat(text, not(containsString("body")));
    }

    @Test
    public void shouldRoundTripWithHeadersAndBodyModel()
    {
        String yaml =
            """
            headers:
              ":method": POST
            body:
              model: test
            """;

        A2aHttpWithConfig with = jsonb.fromJson(yaml, A2aHttpWithConfig.class);
        A2aHttpWithConfig again = jsonb.fromJson(jsonb.toJson(with), A2aHttpWithConfig.class);

        assertThat(again.headers, equalTo(with.headers));
        assertThat(again.body, not(nullValue()));
    }
}
