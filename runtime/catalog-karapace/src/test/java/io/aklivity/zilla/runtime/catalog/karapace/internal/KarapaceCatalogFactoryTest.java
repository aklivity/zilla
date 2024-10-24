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
package io.aklivity.zilla.runtime.catalog.karapace.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogFactory;

public class KarapaceCatalogFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        CatalogFactory factory = CatalogFactory.instantiate();
        Catalog catalog = factory.create(KarapaceCatalogFactorySpi.TYPE, config);

        assertEquals(KarapaceCatalogFactorySpi.TYPE, catalog.name());
        assertEquals(KarapaceCatalogFactorySpi.TYPE_ALIASES, catalog.aliases());
    }
}
