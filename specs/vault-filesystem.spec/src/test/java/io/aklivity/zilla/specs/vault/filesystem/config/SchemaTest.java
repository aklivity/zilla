/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.specs.vault.filesystem.config;

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
        .schemaPatch("io/aklivity/zilla/specs/vault/filesystem/schema/filesystem.schema.patch.json")
        .configurationRoot("io/aklivity/zilla/specs/vault/filesystem/config");

    @Test
    public void shouldValidateVault()
    {
        JsonObject config = schema.validate("vault.yaml");

        assertThat(config, not(nullValue()));
    }

    @Test
    public void shouldValidateVaultWithCRL()
    {
        JsonObject config = schema.validate("vault.crl.yaml");

        assertThat(config, not(nullValue()));
    }
}
