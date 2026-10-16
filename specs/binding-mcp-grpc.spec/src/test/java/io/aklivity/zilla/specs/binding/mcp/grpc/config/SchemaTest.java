/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.specs.binding.mcp.grpc.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/binding/mcp/grpc/schema/mcp_grpc.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/catalog/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/grpc/config");

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientWithOverlay()
    {
        JsonObject config = schema.validate("client.overlay.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientWithMethodRoutes()
    {
        JsonObject config = schema.validate("client.route.method.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientWithOptionRoute()
    {
        JsonObject config = schema.validate("client.route.option.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyKind()
    {
        schema.validate("kind.proxy.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerKind()
    {
        schema.validate("kind.server.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithExit()
    {
        schema.validate("client.exit.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithoutCatalog()
    {
        schema.validate("client.options.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithoutServer()
    {
        schema.validate("client.options.server.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithUnknownOption()
    {
        schema.validate("client.options.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithMultipleCatalogs()
    {
        schema.validate("client.catalog.multiple.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithExit()
    {
        schema.validate("client.route.exit.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenToolWithMethodWildcard()
    {
        schema.validate("client.route.when.wildcard.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenToolWithOption()
    {
        schema.validate("client.route.when.option.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenWithoutWith()
    {
        schema.validate("client.route.when.without.with.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenToolWithInvalidName()
    {
        schema.validate("client.route.when.tool.name.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithMethodAndOption()
    {
        schema.validate("client.route.with.both.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithEmptyOption()
    {
        schema.validate("client.route.option.empty.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithInvalidMethod()
    {
        schema.validate("client.route.method.invalid.yaml");
    }
}
