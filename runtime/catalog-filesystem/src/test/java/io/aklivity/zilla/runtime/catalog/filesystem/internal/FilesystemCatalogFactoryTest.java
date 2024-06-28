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
package io.aklivity.zilla.runtime.catalog.filesystem.internal;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.net.URL;
import java.nio.file.Path;

import org.junit.Test;
import org.mockito.Mockito;

import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemOptionsConfig;
import io.aklivity.zilla.runtime.catalog.filesystem.internal.config.FilesystemSchemaConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogFactory;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class FilesystemCatalogFactoryTest
{
    @Test
    public void shouldLoadAndCreate() throws Exception
    {
        Configuration config = new Configuration();
        CatalogFactory factory = CatalogFactory.instantiate();
        Catalog catalog = factory.create("filesystem", config);

        assertThat(catalog, instanceOf(FilesystemCatalog.class));
        assertEquals("filesystem", catalog.name());

        EngineContext engineContext = mock(EngineContext.class);
        URL url = FilesystemCatalogFactoryTest.class
            .getResource("../../../../specs/catalog/filesystem/config/asyncapi/mqtt.yaml");
        Path path = Path.of(url.toURI());
        Mockito.doReturn(path).when(engineContext).resolvePath("asyncapi/mqtt.yaml");

        CatalogContext context = catalog.supply(engineContext);
        assertThat(context, instanceOf(FilesystemCatalogContext.class));

        FilesystemOptionsConfig catalogConfig =
            new FilesystemOptionsConfig(singletonList(
                new FilesystemSchemaConfig("subject1", "asyncapi/mqtt.yaml")));

        CatalogConfig options = new CatalogConfig("test", "catalog0", "filesystem", catalogConfig);
        CatalogHandler handler = context.attach(options);

        assertThat(handler, instanceOf(FilesystemCatalogHandler.class));
    }
}
