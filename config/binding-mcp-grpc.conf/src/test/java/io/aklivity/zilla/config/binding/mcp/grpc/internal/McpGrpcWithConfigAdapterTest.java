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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.mcp.grpc.McpGrpcWithConfig;

public class McpGrpcWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpGrpcWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithMethod()
    {
        String text =
                "{" +
                    "\"method\": \"example.sql.v1.SQLService/Query\"" +
                "}";

        McpGrpcWithConfig with = jsonb.fromJson(text, McpGrpcWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.method, equalTo("example.sql.v1.SQLService/Query"));
        assertThat(with.option, nullValue());
    }

    @Test
    public void shouldReadWithOption()
    {
        String text =
                "{" +
                    "\"option\": {" +
                        "\"acme.audience\": \"readonly\"," +
                        "\"acme.level\": 2," +
                        "\"acme.internal\": true" +
                    "}" +
                "}";

        McpGrpcWithConfig with = jsonb.fromJson(text, McpGrpcWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.method, nullValue());
        assertThat(with.option.get("acme.audience"), equalTo(Json.createValue("readonly")));
        assertThat(with.option.get("acme.level"), equalTo(Json.createValue(2)));
        assertThat(with.option.get("acme.internal"), equalTo(JsonValue.TRUE));
    }

    @Test
    public void shouldWriteWithMethod()
    {
        McpGrpcWithConfig with = McpGrpcWithConfig.builder()
            .method("example.sql.v1.SQLService/*")
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, equalTo("{\"method\":\"example.sql.v1.SQLService/*\"}"));
    }

    @Test
    public void shouldWriteWithOption()
    {
        McpGrpcWithConfig with = McpGrpcWithConfig.builder()
            .option("acme.audience", Json.createValue("readonly"))
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, equalTo("{\"option\":{\"acme.audience\":\"readonly\"}}"));
    }
}
