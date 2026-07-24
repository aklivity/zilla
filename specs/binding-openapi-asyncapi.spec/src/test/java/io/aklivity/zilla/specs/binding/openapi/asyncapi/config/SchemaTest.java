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
package io.aklivity.zilla.specs.binding.openapi.asyncapi.config;

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
        .schemaPatch("io/aklivity/zilla/config/binding/openapi/asyncapi/" +
            "schema/openapi.asyncapi.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/catalog/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/openapi/asyncapi/config");


    @Test
    public void shouldValidateProxy()
    {
        JsonObject config = schema.validate("proxy.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWithTag()
    {
        JsonObject config = schema.validate("proxy.tag.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWithGlobOperation()
    {
        JsonObject config = schema.validate("proxy.glob.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectProxyWithTagAndOperation()
    {
        schema.validate("proxy.tag.and.operation.invalid.yaml");
    }

    @Test
    public void shouldValidateProxyWithSecurity()
    {
        JsonObject config = schema.validate("proxy.guarded.yaml");

        assertThat(config, not(nullValue()));
    }
}
