/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.tcp.internal.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

public class TcpOptionsAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TcpOptionsAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"host\": \"localhost\"," +
                    "\"port\": 8080" +
                "}";

        TcpOptions options = jsonb.fromJson(text, TcpOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.port, equalTo(8080));
    }

    @Test
    public void shouldWriteOptions()
    {
        TcpOptions options = new TcpOptions("localhost", 8080, 0, true, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"host\":\"localhost\",\"port\":8080}"));
    }

    @Test
    public void shouldReadOptionsWithBacklog()
    {
        String text =
                "{" +
                    "\"host\": \"localhost\"," +
                    "\"port\": 8080," +
                    "\"backlog\": 1000" +
                "}";

        TcpOptions options = jsonb.fromJson(text, TcpOptions.class);

        assertThat(options, not(nullValue()));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.port, equalTo(8080));
        assertThat(options.backlog, equalTo(1000));
    }

    @Test
    public void shouldWriteOptionsWithBacklog()
    {
        TcpOptions options = new TcpOptions("localhost", 8080, 1000, true, false);

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo("{\"host\":\"localhost\",\"port\":8080,\"backlog\":1000}"));
    }
}
