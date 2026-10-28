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
package io.aklivity.zilla.specs.binding.a2a.config;

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
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/store/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/binding/a2a/schema/a2a.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/config");

    @Test
    public void shouldValidateClientDeliver()
    {
        JsonObject config = schema.validate("client.deliver.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientGuarded()
    {
        JsonObject config = schema.validate("client.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientOptions()
    {
        JsonObject config = schema.validate("client.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientRoutes()
    {
        JsonObject config = schema.validate("client.routes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientVersions()
    {
        JsonObject config = schema.validate("client.versions.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClient()
    {
        JsonObject config = schema.validate("client.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheCredentials()
    {
        JsonObject config = schema.validate("proxy.cache.credentials.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCache()
    {
        JsonObject config = schema.validate("proxy.cache.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyDiscoverySearch()
    {
        JsonObject config = schema.validate("proxy.discovery.search.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyDiscovery()
    {
        JsonObject config = schema.validate("proxy.discovery.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyGuarded()
    {
        JsonObject config = schema.validate("proxy.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyOptions()
    {
        JsonObject config = schema.validate("proxy.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyPush()
    {
        JsonObject config = schema.validate("proxy.push.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyRoutes()
    {
        JsonObject config = schema.validate("proxy.routes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerGuarded()
    {
        JsonObject config = schema.validate("server.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerOptions()
    {
        JsonObject config = schema.validate("server.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerTenant()
    {
        JsonObject config = schema.validate("server.tenant.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerVersionsV03()
    {
        JsonObject config = schema.validate("server.versions.v03.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerVersionsV10()
    {
        JsonObject config = schema.validate("server.versions.v10.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServer()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientExitMissing()
    {
        schema.validate("client.exit.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientOptionUnknown()
    {
        schema.validate("client.option.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRoutesExitMissing()
    {
        schema.validate("client.routes.exit.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientRoutesSkillUnknown()
    {
        schema.validate("client.routes.skill.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientServer()
    {
        schema.validate("client.server.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientVersionsUnknown()
    {
        schema.validate("client.versions.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheStoreMissing()
    {
        schema.validate("proxy.cache.store.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyDiscoverySearchField()
    {
        schema.validate("proxy.discovery.search.field.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyDiscoverySearchTypeIndex()
    {
        schema.validate("proxy.discovery.search.type.index.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyDiscoverySearchType()
    {
        schema.validate("proxy.discovery.search.type.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyOptionUnknown()
    {
        schema.validate("proxy.option.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyPushAllowEmpty()
    {
        schema.validate("proxy.push.allow.empty.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyPushVaultMissing()
    {
        schema.validate("proxy.push.vault.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRoutesAgentMissing()
    {
        schema.validate("proxy.routes.agent.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRoutesAgentUnknown()
    {
        schema.validate("proxy.routes.agent.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerAuthorizationCredentialsPlaceholder()
    {
        schema.validate("server.authorization.credentials.placeholder.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerExitMissing()
    {
        schema.validate("server.exit.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerOptionUnknown()
    {
        schema.validate("server.option.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerPushUnknown()
    {
        schema.validate("server.push.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerRoutes()
    {
        schema.validate("server.routes.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerUrl()
    {
        schema.validate("server.url.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerVersionsEmpty()
    {
        schema.validate("server.versions.empty.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerVersionsUnknown()
    {
        schema.validate("server.versions.unknown.invalid.yaml");
    }

    @Test
    public void shouldValidateClientCredentials()
    {
        JsonObject config = schema.validate("client.credentials.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientRoutesGuarded()
    {
        JsonObject config = schema.validate("client.routes.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateClientVersionsV03()
    {
        JsonObject config = schema.validate("client.versions.v03.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheGuarded()
    {
        JsonObject config = schema.validate("proxy.cache.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheRefresh()
    {
        JsonObject config = schema.validate("proxy.cache.refresh.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheRestricted()
    {
        JsonObject config = schema.validate("proxy.cache.restricted.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyRoutesGuarded()
    {
        JsonObject config = schema.validate("proxy.routes.guarded.yaml");

        assertThat(config, not(nullValue()));
    }
}
