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
package io.aklivity.zilla.specs.binding.mcp.openapi.config;

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
        .schemaPatch("io/aklivity/zilla/config/binding/mcp/openapi/schema/mcp_openapi.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/config/catalog/inline/schema/inline.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/model/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/mcp/openapi/config");

    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

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
    public void shouldRejectBulkRouteNamingToolOrResource()
    {
        schema.validate("proxy.route.invalid.yaml");
    }

    @Test
    public void shouldValidateBulkRoutes()
    {
        JsonObject config = schema.validate("proxy.route.bulk.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectTagAndOperationTogether()
    {
        schema.validate("proxy.route.tag.and.operation.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectUnknownCapability()
    {
        schema.validate("proxy.route.capability.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectMissingRoutes()
    {
        schema.validate("proxy.routes.missing.invalid.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectEmptyRoutes()
    {
        schema.validate("proxy.routes.empty.invalid.yaml");
    }

    @Test
    public void shouldValidateInputSchemaAndParams()
    {
        JsonObject config = schema.validate("proxy.input.schema.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectResourceInputSchema()
    {
        schema.validate("proxy.resource.input.schema.invalid.yaml");
    }
}
