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
package io.aklivity.zilla.specs.binding.http.filesystem.config;

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
        .schemaPatch("io/aklivity/zilla/specs/binding/http/filesystem/schema/http.filesystem.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/binding/http/filesystem/config");

    @Test
    public void shouldValidateProxyWithPath()
    {
        JsonObject config = schema.validate("proxy.with.path.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateProxyWithDynamicDirectory()
    {
        JsonObject config = schema.validate("proxy.with.directory.dynamic.yaml");

        assertThat(config, not(nullValue()));
    }
}
