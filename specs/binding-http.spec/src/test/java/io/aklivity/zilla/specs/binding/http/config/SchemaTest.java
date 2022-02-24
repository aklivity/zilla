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
package io.aklivity.zilla.specs.binding.http.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/http/schema/http.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/http/config");

    @Test
    public void shouldValidateUpgradeServer()
    {
        JsonObject config = schema.validate("upgrade/server.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp11Client()
    {
        JsonObject config = schema.validate("v1.1/client.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp11ClientOverride()
    {
        JsonObject config = schema.validate("v1.1/client.override.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp11Server()
    {
        JsonObject config = schema.validate("v1.1/server.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp11ServerAuthority()
    {
        JsonObject config = schema.validate("v1.1/server.authority.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp11ServerOverride()
    {
        JsonObject config = schema.validate("v1.1/server.override.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp11ServerAccessControlPolicy()
    {
        JsonObject config = schema.validate("v1.1/server.access.control.policy.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp1ServerAccessControlAllow()
    {
        JsonObject config = schema.validate("v1.1/server.access.control.allow.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp1ServerAccessControlAllowCredentials()
    {
        JsonObject config = schema.validate("v1.1/server.access.control.allow.credentials.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp1ServerAccessControlMaxAge()
    {
        JsonObject config = schema.validate("v1.1/server.access.control.max.age.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp1ServerAccessControlExpose()
    {
        JsonObject config = schema.validate("v1.1/server.access.control.expose.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2Client()
    {
        JsonObject config = schema.validate("v2/client.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ClientOverride()
    {
        JsonObject config = schema.validate("v2/client.override.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2Server()
    {
        JsonObject config = schema.validate("v2/server.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttpServerAuthority()
    {
        JsonObject config = schema.validate("v2/server.authority.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ServerOverride()
    {
        JsonObject config = schema.validate("v2/server.override.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ServerAccessControlPolicy()
    {
        JsonObject config = schema.validate("v2/server.access.control.policy.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ServerAccessControlAllow()
    {
        JsonObject config = schema.validate("v2/server.access.control.allow.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ServerAccessControlAllowCredentials()
    {
        JsonObject config = schema.validate("v2/server.access.control.allow.credentials.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ServerAccessControlMaxAge()
    {
        JsonObject config = schema.validate("v2/server.access.control.max.age.json");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateHttp2ServerAccessControlExpose()
    {
        JsonObject config = schema.validate("v2/server.access.control.expose.json");

        assertThat(config, not(nullValue()));
    }
}
