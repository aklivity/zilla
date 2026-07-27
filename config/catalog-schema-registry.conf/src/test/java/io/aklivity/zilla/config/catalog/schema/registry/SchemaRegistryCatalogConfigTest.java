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
package io.aklivity.zilla.config.catalog.schema.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class SchemaRegistryCatalogConfigTest
{
    @Test
    public void shouldBuildWithTypedOptions()
    {
        SchemaRegistryCatalogConfig catalog = SchemaRegistryCatalogConfig.builder()
            .namespace("test")
            .name("schemaregistry0")
            .vault("vault0")
            .options()
                .url("http://localhost:8081")
                .context("ctx0")
                .build()
            .build();

        assertThat(catalog.namespace, equalTo("test"));
        assertThat(catalog.name, equalTo("schemaregistry0"));
        assertThat(catalog.type, equalTo("schema-registry"));
        assertThat(catalog.vault, equalTo("vault0"));
        assertThat(((SchemaRegistryOptionsConfig) catalog.options).url, equalTo("http://localhost:8081"));
    }

    @Test
    public void shouldBuildViaMapper()
    {
        SchemaRegistryCatalogConfig catalog = SchemaRegistryCatalogConfig.builder(SchemaRegistryCatalogConfig.class::cast)
            .namespace("test")
            .name("schemaregistry1")
            .build();

        assertThat(catalog.name, equalTo("schemaregistry1"));
    }
}
