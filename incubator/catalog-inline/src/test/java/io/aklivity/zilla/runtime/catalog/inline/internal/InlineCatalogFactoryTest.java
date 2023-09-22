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
package io.aklivity.zilla.runtime.catalog.inline.internal;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import io.aklivity.zilla.runtime.catalog.inline.internal.config.InlineOptionsConfig;
import io.aklivity.zilla.runtime.catalog.inline.internal.config.InlineSchemaConfig;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogFactory;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class InlineCatalogFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        CatalogFactory factory = CatalogFactory.instantiate();
        Catalog catalog = factory.create("inline", config);

        assertThat(catalog, instanceOf(InlineCatalog.class));
        assertEquals("inline", catalog.name());

        CatalogContext context = catalog.supply(mock(EngineContext.class));
        assertThat(context, instanceOf(InlineCatalogContext.class));

        InlineOptionsConfig catalogConfig =
                new InlineOptionsConfig(singletonList(
                        new InlineSchemaConfig("subject1", "latest", "{\"type\": \"string\"}")));
        CatalogConfig options = new CatalogConfig("catalog0", "inline", catalogConfig);
        CatalogHandler handler = context.attach(options);
        assertThat(handler, instanceOf(InlineCatalogHandler.class));
    }
}
