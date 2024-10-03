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
package io.aklivity.zilla.specs.binding.openapi.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/openapi/schema/openapi.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/tls/schema/tls.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/tcp/schema/tcp.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/http/schema/http.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/vault/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/catalog/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/openapi/config");

    @Test
    public void shouldValidateServerWithProductionEnvironment()
    {
        JsonObject config = schema.validate("server.env.prod.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWithMultipleSpecifications()
    {
        JsonObject config = schema.validate("server.multiple.specs.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWithHttpDefaultPort()
    {
        JsonObject config = schema.validate("server.port.http.default.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerWithHttpsDefaultPort()
    {
        JsonObject config = schema.validate("server.port.https.default.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerSecure()
    {
        JsonObject config = schema.validate("server.secure.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientWithMultipleSpecifications()
    {
        JsonObject config = schema.validate("client.multiple.specs.yaml");

        assertThat(config, not(nullValue()));
    }
}
