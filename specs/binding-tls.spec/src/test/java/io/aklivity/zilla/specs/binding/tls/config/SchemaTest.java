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
package io.aklivity.zilla.specs.binding.tls.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/tls/schema/tls.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/vault/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/tls/config");

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientWithOptionsDefault()
    {
        JsonObject config = schema.validate("client.options.default.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientAlpnDefault()
    {
        JsonObject config = schema.validate("client.alpn.default.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientAlpn()
    {
        JsonObject config = schema.validate("client.alpn.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientCaCerts()
    {
        JsonObject config = schema.validate("client.cacerts.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientMutual()
    {
        JsonObject config = schema.validate("client.mutual.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientMutualSigner()
    {
        JsonObject config = schema.validate("client.mutual.signer.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSni()
    {
        JsonObject config = schema.validate("client.sni.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientProxyName()
    {
        JsonObject config = schema.validate("proxy.sni.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerAlpnDefault()
    {
        JsonObject config = schema.validate("server.alpn.default.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerAlpn()
    {
        JsonObject config = schema.validate("server.alpn.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerKeysNotFound()
    {
        JsonObject config = schema.validate("server.keys.not.found.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerMutual()
    {
        JsonObject config = schema.validate("server.mutual.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerMutualRequested()
    {
        JsonObject config = schema.validate("server.mutual.requested.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSigner()
    {
        JsonObject config = schema.validate("server.signer.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSni()
    {
        JsonObject config = schema.validate("server.sni.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerPorts()
    {
        JsonObject config = schema.validate("server.ports.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientPorts()
    {
        JsonObject config = schema.validate("client.ports.yaml");

        assertThat(config, not(nullValue()));
    }
}
