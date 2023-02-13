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
package io.aklivity.zilla.specs.binding.proxy.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/binding/proxy/schema/proxy.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/proxy/config");

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientTcp()
    {
        JsonObject config = schema.validate("client.tcp.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSockStream()
    {
        JsonObject config = schema.validate("server.sock.stream.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerTcp4Alpn()
    {
        JsonObject config = schema.validate("server.tcp4.alpn.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerTcp4()
    {
        JsonObject config = schema.validate("server.tcp4.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerTcp4SslVersion()
    {
        JsonObject config = schema.validate("server.tcp4.ssl.version.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerTcp6()
    {
        JsonObject config = schema.validate("server.tcp6.yaml");

        assertThat(config, not(nullValue()));
    }
}
