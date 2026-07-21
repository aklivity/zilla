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
package io.aklivity.zilla.specs.binding.mcp.http.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/mcp/http/schema/mcp_http.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/model/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/http/config");

    @Test
    public void shouldValidateProxyOptions()
    {
        JsonObject config = schema.validate("proxy.options.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectInvalidKind()
    {
        schema.validate("proxy.kind.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectUnknownOption()
    {
        schema.validate("proxy.options.unknown.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectToolWithoutSchemas()
    {
        schema.validate("proxy.tool.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectResourceWithoutUri()
    {
        schema.validate("proxy.resource.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithoutHeaders()
    {
        schema.validate("proxy.route.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithWithButNoWhen()
    {
        schema.validate("proxy.route.when.required.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWhenWithBothToolAndResource()
    {
        schema.validate("proxy.route.when.both.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectRouteWithMultipleWhenEntries()
    {
        schema.validate("proxy.route.when.multiple.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectAuthorityWithoutPort()
    {
        schema.validate("proxy.route.headers.authority.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectSchemeOtherThanHttpOrHttps()
    {
        schema.validate("proxy.route.headers.scheme.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectHeadersWithoutPath()
    {
        schema.validate("proxy.route.headers.path.invalid.yaml");
    }

    @Test
    public void shouldValidateGuardedRouteWithoutWhen()
    {
        JsonObject config = schema.validate("proxy.guarded.global.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateGuardedRouteScopedByWhenWithoutWith()
    {
        JsonObject config = schema.validate("proxy.guarded.scoped.yaml");

        assertThat(config, not(nullValue()));
    }
}
