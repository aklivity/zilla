/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.schema.registry.internal.config.SchemaRegistryOptionsConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogFactory;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class SchemaRegistryCatalogFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        CatalogFactory factory = CatalogFactory.instantiate();
        Catalog catalog = factory.create("schema-registry", config);

        assertThat(catalog, instanceOf(SchemaRegistryCatalog.class));
        assertEquals("schema-registry", catalog.name());

        CatalogContext context = catalog.supply(mock(EngineContext.class));
        assertThat(context, instanceOf(SchemaRegistryCatalogContext.class));

        SchemaRegistryOptionsConfig catalogConfig = SchemaRegistryOptionsConfig.builder()
            .url("http://localhost:8081")
            .context("default")
            .maxAge(Duration.ofSeconds(100))
            .build();
        CatalogConfig options = new CatalogConfig("catalog0", "schema-registry", catalogConfig);
        CatalogHandler handler = context.attach(options);
        assertThat(handler, instanceOf(SchemaRegistryCatalogHandler.class));
    }
}
