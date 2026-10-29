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
package io.aklivity.zilla.specs.binding.a2a.openapi.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/a2a/openapi/schema/a2a_openapi.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/model/json/schema/json.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/catalog/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/embedding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/store/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/a2a/openapi/config");

    @Test
    public void shouldValidateProxyAgent()
    {
        JsonObject config = schema.validate("proxy.agent.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyCredentials()
    {
        JsonObject config = schema.validate("proxy.credentials.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyExit()
    {
        JsonObject config = schema.validate("proxy.exit.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyGuarded()
    {
        JsonObject config = schema.validate("proxy.guarded.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyMulti()
    {
        JsonObject config = schema.validate("proxy.multi.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyOverlay()
    {
        JsonObject config = schema.validate("proxy.overlay.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyParams()
    {
        JsonObject config = schema.validate("proxy.params.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyRouteOperation()
    {
        JsonObject config = schema.validate("proxy.route.operation.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyRouteTag()
    {
        JsonObject config = schema.validate("proxy.route.tag.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyServer()
    {
        JsonObject config = schema.validate("proxy.server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxySkills()
    {
        JsonObject config = schema.validate("proxy.skills.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxySse()
    {
        JsonObject config = schema.validate("proxy.sse.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyXZillaA2A()
    {
        JsonObject config = schema.validate("proxy.x-zilla-a2a.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyEmbeddingMissing()
    {
        schema.validate("proxy.embedding.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyExitMissing()
    {
        schema.validate("proxy.exit.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyKind()
    {
        schema.validate("proxy.kind.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyOptionsUnknown()
    {
        schema.validate("proxy.options.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRouteBody()
    {
        schema.validate("proxy.route.body.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRoute()
    {
        schema.validate("proxy.route.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRouteTagAndOperation()
    {
        schema.validate("proxy.route.tag.and.operation.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRouteWhenBulk()
    {
        schema.validate("proxy.route.when.bulk.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRoutesEmpty()
    {
        schema.validate("proxy.routes.empty.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyRoutesMissing()
    {
        schema.validate("proxy.routes.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxySkillsUnknown()
    {
        schema.validate("proxy.skills.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxySpecsMissing()
    {
        schema.validate("proxy.specs.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyStoreMissing()
    {
        schema.validate("proxy.store.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyThreshold()
    {
        schema.validate("proxy.threshold.invalid.yaml");
    }
}
