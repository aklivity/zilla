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
package io.aklivity.zilla.specs.binding.tcp.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/tcp/schema/tcp.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/tcp/config");

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerIPv6()
    {
        JsonObject config = schema.validate("server.ipv6.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerPorts()
    {
        JsonObject config = schema.validate("server.ports.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientAuthority()
    {
        JsonObject config = schema.validate("client.authority.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientHostAndSubnetIPv6()
    {
        JsonObject config = schema.validate("client.host.and.subnet.ipv6.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientHostAndSubnet()
    {
        JsonObject config = schema.validate("client.host.and.subnet.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientHost()
    {
        JsonObject config = schema.validate("client.host.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientIP()
    {
        JsonObject config = schema.validate("client.ip.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientIPv6()
    {
        JsonObject config = schema.validate("client.ipv6.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSubnetIPv6()
    {
        JsonObject config = schema.validate("client.subnet.ipv6.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSubnet()
    {
        JsonObject config = schema.validate("client.subnet.yaml");

        assertThat(config, not(nullValue()));
    }
}
