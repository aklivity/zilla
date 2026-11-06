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
package io.aklivity.zilla.config.binding.a2a.openapi.internal;

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

import io.aklivity.zilla.config.binding.a2a.openapi.A2aOpenapiWithConfig;

public class A2aOpenapiWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
            .withAdapters(new A2aOpenapiWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithParams()
    {
        String text =
            """
            {
              "spec": "github",
              "operation": "create_pr",
              "params": {
                "owner": "${args.repository.owner}"
              }
            }
            """;

        A2aOpenapiWithConfig with = jsonb.fromJson(text, A2aOpenapiWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.spec, equalTo("github"));
        assertThat(with.operation, equalTo("create_pr"));
        assertThat(with.tag, nullValue());
        assertThat(with.params, equalTo(Map.of("owner", "${args.repository.owner}")));
    }

    @Test
    public void shouldReadWithTag()
    {
        A2aOpenapiWithConfig with = jsonb.fromJson("{\"spec\":\"github\",\"tag\":\"orders\"}", A2aOpenapiWithConfig.class);

        assertThat(with.spec, equalTo("github"));
        assertThat(with.tag, equalTo("orders"));
        assertThat(with.operation, nullValue());
        assertThat(with.params, nullValue());
    }

    @Test
    public void shouldWriteWithParams()
    {
        A2aOpenapiWithConfig with = A2aOpenapiWithConfig.builder()
            .spec("github")
            .operation("create_pr")
            .params(Map.of("owner", "${args.repository.owner}"))
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, equalTo(
            "{\"spec\":\"github\",\"operation\":\"create_pr\",\"params\":{\"owner\":\"${args.repository.owner}\"}}"));
    }

    @Test
    public void shouldWriteWithNoParams()
    {
        A2aOpenapiWithConfig with = A2aOpenapiWithConfig.builder()
            .spec("github")
            .tag("orders")
            .build();

        String text = jsonb.toJson(with);

        assertThat(text, equalTo("{\"spec\":\"github\",\"tag\":\"orders\"}"));
    }
}
