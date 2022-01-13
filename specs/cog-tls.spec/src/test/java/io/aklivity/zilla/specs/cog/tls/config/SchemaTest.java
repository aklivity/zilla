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
package io.aklivity.zilla.specs.cog.tls.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/cog/tls/schema/tls.json")
        .schemaPatch("io/aklivity/zilla/specs/vault/filesystem/schema/filesystem.json")
        .configurationRoot("io/aklivity/zilla/specs/cog/tls/config");

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientAlpnDefault()
    {
        JsonObject config = schema.validate("client.alpn.default.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientAlpn()
    {
        JsonObject config = schema.validate("client.alpn.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientCaCerts()
    {
        JsonObject config = schema.validate("client.cacerts.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientMutual()
    {
        JsonObject config = schema.validate("client.mutual.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientMutualSigner()
    {
        JsonObject config = schema.validate("client.mutual.signer.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientSni()
    {
        JsonObject config = schema.validate("client.sni.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientProxyName()
    {
        JsonObject config = schema.validate("proxy.sni.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerAlpnDefault()
    {
        JsonObject config = schema.validate("server.alpn.default.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerAlpn()
    {
        JsonObject config = schema.validate("server.alpn.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerMutual()
    {
        JsonObject config = schema.validate("server.mutual.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerMutualRequested()
    {
        JsonObject config = schema.validate("server.mutual.requested.json");

        assertThat(config, not(nullValue()));
    }

    @Ignore("TODO: realms")
    @Test
    public void shouldValidateServerRealm()
    {
        JsonObject config = schema.validate("server.realm.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSigner()
    {
        JsonObject config = schema.validate("server.signer.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSni()
    {
        JsonObject config = schema.validate("server.sni.json");

        assertThat(config, not(nullValue()));
    }
}
