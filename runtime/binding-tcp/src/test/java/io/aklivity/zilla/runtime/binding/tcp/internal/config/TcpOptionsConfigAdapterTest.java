/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal.config;

import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public class TcpOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new TcpOptionsConfigAdapter());
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
                host: localhost
                port: 12345
                """;

        TcpOptionsConfig options = jsonb.fromJson(yaml, TcpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.ports, not(nullValue()));
        assertThat(options.ports.length, equalTo(1));
        assertThat(options.ports[0], equalTo(12345));
    }

    @Test
    public void shouldReadOptionsWithPortRange()
    {
        String yaml =
                """
                host: localhost
                port: "12345-12346"
                """;

        TcpOptionsConfig options = jsonb.fromJson(yaml, TcpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.ports, not(nullValue()));
        assertThat(options.ports.length, equalTo(2));
        assertThat(options.ports[0], equalTo(12345));
        assertThat(options.ports[1], equalTo(12346));
    }

    @Test
    public void shouldReadOptionsWithPortRangeSingleton()
    {
        String yaml =
                """
                host: localhost
                port: "12345"
                """;

        TcpOptionsConfig options = jsonb.fromJson(yaml, TcpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.ports, not(nullValue()));
        assertThat(options.ports.length, equalTo(1));
        assertThat(options.ports[0], equalTo(12345));
    }

    @Test
    public void shouldWriteOptions()
    {
        TcpOptionsConfig options = TcpOptionsConfig.builder()
            .inject(identity())
            .host("localhost")
            .ports(new int[] { 12345 })
            .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                host: localhost
                port: 12345
                """));
    }

    @Test
    public void shouldReadOptionsWithBacklog()
    {
        String yaml =
                """
                host: localhost
                port: 12345
                backlog: 1000
                """;

        TcpOptionsConfig options = jsonb.fromJson(yaml, TcpOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.host, equalTo("localhost"));
        assertThat(options.ports, not(nullValue()));
        assertThat(options.ports.length, equalTo(1));
        assertThat(options.ports[0], equalTo(12345));
        assertThat(options.backlog, equalTo(1000));
    }

    @Test
    public void shouldWriteOptionsWithBacklog()
    {
        TcpOptionsConfig options = TcpOptionsConfig.builder()
                .inject(identity())
                .host("localhost")
                .ports(new int[] { 12345 })
                .backlog(1000)
                .build();

        String yaml = jsonb.toJson(options);

        assertThat(yaml, not(nullValue()));
        assertThat(yaml, equalTo(
                """
                host: localhost
                port: 12345
                backlog: 1000
                """));
    }
}
