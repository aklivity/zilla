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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class CatalogRegistryTest
{
    @Test
    public void shouldWork()
    {
        // GIVEN
        CatalogConfig config = new CatalogConfig("test", "test", null);
        CatalogContext context = new CatalogContext()
        {
        };
        CatalogRegistry catalog = new CatalogRegistry(config, context);

        // WHEN
        catalog.attach();

        // THEN
        assertThat(catalog.handler(), nullValue());
    }
}
