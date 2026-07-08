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

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

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

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRouteMissingToolkit()
    {
        schema.validate("proxy.routes.missing.toolkit.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWithAuthorization()
    {
        schema.validate("proxy.authorization.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerWithCache()
    {
        schema.validate("server.cache.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerWithRoutes()
    {
        schema.validate("server.routes.invalid.yaml");
    }

    @Test
    public void shouldValidateServerWithAuthorizationCredentialsDefault()
    {
        JsonObject config = schema.validate("server.authorization.credentials.default.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerWithAuthorizationCredentialsMissingPlaceholder()
    {
        schema.validate("server.authorization.credentials.placeholder.invalid.yaml");
    }

    @Test
    public void shouldValidateClientWithRoutes()
    {
        JsonObject config = schema.validate("client.routes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithoutServer()
    {
        schema.validate("client.server.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithNonHttpServer()
    {
        schema.validate("client.server.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectClientWithElicitation()
    {
        schema.validate("client.elicitation.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWithElicitation()
    {
        schema.validate("proxy.elicitation.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRouteWithHeaders()
    {
        schema.validate("proxy.headers.invalid.yaml");
    }

    @Test
    public void shouldValidateProxyCacheToolsSearch()
    {
        JsonObject config = schema.validate("proxy.cache.tools.search.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheToolsSearchWithType()
    {
        JsonObject config = schema.validate("proxy.cache.tools.search.type.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheToolsSearchWithIndex()
    {
        JsonObject config = schema.validate("proxy.cache.tools.search.index.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsSearchWithUnknownType()
    {
        schema.validate("proxy.cache.tools.search.type.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsSearchWithTypeAndIndex()
    {
        schema.validate("proxy.cache.tools.search.type.index.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsSearchIndexWithAdditionalProperty()
    {
        schema.validate("proxy.cache.tools.search.index.additional.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsSearchMissingTool()
    {
        schema.validate("proxy.cache.tools.search.tool.missing.invalid.yaml");
    }

    @Test
    public void shouldValidateProxyCacheToolsEager()
    {
        JsonObject config = schema.validate("proxy.cache.tools.eager.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheToolsEagerAll()
    {
        JsonObject config = schema.validate("proxy.cache.tools.eager.all.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCacheToolsEagerExplicit()
    {
        JsonObject config = schema.validate("proxy.cache.tools.eager.explicit.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsEagerWithUnknownPolicy()
    {
        schema.validate("proxy.cache.tools.eager.policy.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsEagerExplicitMissingMatch()
    {
        schema.validate("proxy.cache.tools.eager.explicit.match.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsEagerExplicitEmptyMatch()
    {
        schema.validate("proxy.cache.tools.eager.explicit.match.empty.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsEagerNoneWithMatch()
    {
        schema.validate("proxy.cache.tools.eager.none.match.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyCacheToolsEagerWithAdditionalProperty()
    {
        schema.validate("proxy.cache.tools.eager.additional.invalid.yaml");
    }
}
