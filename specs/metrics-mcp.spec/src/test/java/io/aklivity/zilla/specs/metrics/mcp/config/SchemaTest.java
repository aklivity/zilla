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
package io.aklivity.zilla.specs.metrics.mcp.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.JsonObject;

import org.junit.Rule;
import org.junit.Test;

import io.aklivity.zilla.specs.engine.config.ConfigSchemaRule;

public class SchemaTest
{
    @Rule
    public final ConfigSchemaRule schema = new ConfigSchemaRule()
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/binding/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/engine/schema/exporter/test.schema.patch.json")
        .schemaPatch("io/aklivity/zilla/specs/metrics/mcp/schema/mcp.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/metrics/mcp/config");

    @Test
    public void shouldValidateToolsCall()
    {
        JsonObject config = schema.validate("tools.call.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateToolsCallAttributes()
    {
        JsonObject config = schema.validate("tools.call.attributes.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateToolsCallIsError()
    {
        JsonObject config = schema.validate("tools.call.is.error.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateToolsCallAborted()
    {
        JsonObject config = schema.validate("tools.call.aborted.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateToolsList()
    {
        JsonObject config = schema.validate("tools.list.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidatePromptsGet()
    {
        JsonObject config = schema.validate("prompts.get.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateResourcesRead()
    {
        JsonObject config = schema.validate("resources.read.yaml");

        assertThat(config, not(nullValue()));
    }
}
