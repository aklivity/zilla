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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mcp.config.McpWithConfig;

public class McpWithConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new McpWithConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadWithCacheCredentials()
    {
        String text = "{\"cache\":{\"credentials\":\"{token}\"}}";

        McpWithConfig with = jsonb.fromJson(text, McpWithConfig.class);

        assertThat(with, not(nullValue()));
        assertThat(with.cache, not(nullValue()));
        assertThat(with.cache.credentials, equalTo("{token}"));
    }

    @Test
    public void shouldWriteWithCacheCredentials()
    {
        McpWithConfig with = McpWithConfig.builder()
                .cache()
                    .credentials("{token}")
                    .build()
                .build();

        String text = jsonb.toJson(with);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"cache\":{\"credentials\":\"{token}\"}}"));
    }
}
