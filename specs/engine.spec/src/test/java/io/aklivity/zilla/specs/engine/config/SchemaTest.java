/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.specs.engine.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonException;
import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/exporter/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/guard/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/metrics/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/validator/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/vault/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/catalog/test.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/engine/config");

    @Test
    public void shouldValidateServerBinding()
    {
        JsonObject config = schema.validate("server.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateServerBindingWithRoutesAndNoExit()
    {
        JsonObject config = schema.validate("server.binding.with.routes.and.no.exit.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerBindingWithNoType()
    {
        schema.validate("server.binding.with.no.type.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerBindingWithNoKind()
    {
        schema.validate("server.binding.with.no.kind.yaml");
    }

    @Test(expected = JsonException.class)
    public void shouldRejectServerBindingWithNoExit()
    {
        schema.validate("server.binding.with.no.exit.yaml");
    }
}
