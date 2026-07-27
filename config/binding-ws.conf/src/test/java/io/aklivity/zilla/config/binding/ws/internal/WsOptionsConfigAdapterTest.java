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
package io.aklivity.zilla.config.binding.ws.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.binding.ws.WsOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class WsOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new WsOptionsConfigAdapter());
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
                defaults:
                  protocol: echo
                  authority: "example.net:443"
                """;

        WsOptionsConfig options = jsonb.fromJson(yaml, WsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.protocol, equalTo("echo"));
        assertThat(options.authority, equalTo("example.net:443"));
    }

    @Test
    public void shouldWriteOptions()
    {
        WsOptionsConfig options = WsOptionsConfig.builder()
                .protocol("echo")
                .authority("example.net:443")
                .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                defaults:
                  protocol: echo
                  authority: "example.net:443"
                """));
    }
}
