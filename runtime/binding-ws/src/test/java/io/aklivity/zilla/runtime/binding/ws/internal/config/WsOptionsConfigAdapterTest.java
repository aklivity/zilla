/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.ws.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class WsOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new WsOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"defaults\":" +
                    "{" +
                        "\"protocol\": \"echo\"," +
                        "\"authority\": \"example.net:443\"" +
                    "}" +
                "}";

        WsOptionsConfig options = jsonb.fromJson(text, WsOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.protocol, equalTo("echo"));
        assertThat(options.authority, equalTo("example.net:443"));
    }

    @Test
    public void shouldWriteOptions()
    {
        WsOptionsConfig options = new WsOptionsConfig("echo", null, "example.net:443", null);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"defaults\":{\"protocol\":\"echo\",\"authority\":\"example.net:443\"}}"));
    }
}
