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
package io.aklivity.zilla.specs.binding.agui.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/agui/schema/agui.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/agui/config");

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerGuarded()
    {
        JsonObject config = schema.validate("server.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerAuthorizationCredentialsPlaceholderMissing()
    {
        schema.validate("server.authorization.credentials.placeholder.invalid.yaml");
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateChain()
    {
        JsonObject config = schema.validate("chain.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientGuarded()
    {
        JsonObject config = schema.validate("client.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientExitMissing()
    {
        schema.validate("client.exit.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientServerMissing()
    {
        schema.validate("client.server.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientServerInvalid()
    {
        schema.validate("client.server.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientOptionsUnknown()
    {
        schema.validate("client.options.unknown.invalid.yaml");
    }

    @Test
    public void shouldValidateClientRoutes()
    {
        JsonObject config = schema.validate("client.routes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRouteWhenAgentMissing()
    {
        schema.validate("client.route.when.agent.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRouteWhenMultiple()
    {
        schema.validate("client.route.when.multiple.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRouteWhenUnknown()
    {
        schema.validate("client.route.when.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRouteWhenRequired()
    {
        schema.validate("client.route.when.required.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRouteWithUnknown()
    {
        schema.validate("client.route.with.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRouteExitRequired()
    {
        schema.validate("client.route.exit.required.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientAuthorizationCredentialsPlaceholderMissing()
    {
        schema.validate("client.authorization.credentials.placeholder.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientAuthorizationGuardsMultiple()
    {
        schema.validate("client.authorization.guards.multiple.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerOptionsUnknown()
    {
        schema.validate("server.options.unknown.invalid.yaml");
    }

    @Test
    public void shouldValidateServerRoutes()
    {
        JsonObject config = schema.validate("server.routes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerRoutesWhenPathInvalid()
    {
        schema.validate("server.routes.when.path.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerRoutesWhenMethod()
    {
        schema.validate("server.routes.when.method.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerRoutesWithAgentMissing()
    {
        schema.validate("server.routes.with.agent.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerRoutesWithUnknown()
    {
        schema.validate("server.routes.with.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerAuthorizationGuardsMultiple()
    {
        schema.validate("server.authorization.guards.multiple.invalid.yaml");
    }
}
