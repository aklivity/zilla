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
package io.aklivity.zilla.specs.binding.agui.a2a.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/agui/a2a/schema/agui_a2a.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/agui/a2a/config");

    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyExit()
    {
        JsonObject config = schema.validate("proxy.exit.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCatchAll()
    {
        JsonObject config = schema.validate("proxy.catch.all.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyRouteAgentRewrite()
    {
        JsonObject config = schema.validate("proxy.route.agent.rewrite.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyRouteIdentity()
    {
        JsonObject config = schema.validate("proxy.route.identity.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateGuardedProxy()
    {
        JsonObject config = schema.validate("proxy.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerKind()
    {
        schema.validate("proxy.kind.server.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientKind()
    {
        schema.validate("proxy.kind.client.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectOptions()
    {
        schema.validate("proxy.options.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectEmptyOptions()
    {
        schema.validate("proxy.options.empty.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithoutExit()
    {
        schema.validate("proxy.route.exit.required.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenWithUnknownMember()
    {
        schema.validate("proxy.route.when.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithUnknownMember()
    {
        schema.validate("proxy.route.with.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithAgentOfInvalidType()
    {
        schema.validate("proxy.route.with.agent.type.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectVault()
    {
        schema.validate("proxy.vault.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectCatalog()
    {
        schema.validate("proxy.catalog.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectEntry()
    {
        schema.validate("proxy.entry.invalid.yaml");
    }
}
