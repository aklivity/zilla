/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.mcp.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.leadpony.justify.api.JsonValidatingException;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/binding/mcp/schema/mcp.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/config");

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyFilter()
    {
        JsonObject config = schema.validate("proxy.filter.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonValidatingException.class)
    public void shouldRejectProxyRouteMissingToolkit()
    {
        schema.validate("proxy.routes.missing.toolkit.invalid.yaml");
    }

    @Test(expected = JsonValidatingException.class)
    public void shouldRejectProxyRouteFilterCapabilityMismatch()
    {
        schema.validate("proxy.routes.filter.capability.invalid.yaml");
    }

    @Test(expected = JsonValidatingException.class)
    public void shouldRejectProxyWithAuthorization()
    {
        schema.validate("proxy.authorization.invalid.yaml");
    }

    @Test(expected = JsonValidatingException.class)
    public void shouldRejectServerWithCache()
    {
        schema.validate("server.cache.invalid.yaml");
    }

    @Test(expected = JsonValidatingException.class)
    public void shouldRejectServerRouteWithToolkit()
    {
        schema.validate("server.toolkit.invalid.yaml");
    }
}
