/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.catalog;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

public class CatalogHandlerTest
{
    @Test
    public void shouldDelegateAuthorizedResolveToUnauthorizedResolveByDefault()
    {
        CatalogHandler handler = new CatalogHandler()
        {
            @Override
            public String resolve(
                int schemaId)
            {
                return null;
            }

            @Override
            public int resolve(
                String subject,
                String version)
            {
                return 42;
            }
        };

        assertThat(handler.resolve("subject", "latest", 0L), equalTo(42));
        assertThat(handler.resolve("subject", "latest", 1L), equalTo(42));
    }

    @Test
    public void shouldPreferAuthorizedResolveOverrideWhenProvided()
    {
        CatalogHandler handler = new CatalogHandler()
        {
            @Override
            public String resolve(
                int schemaId)
            {
                return null;
            }

            @Override
            public int resolve(
                String subject,
                String version)
            {
                return CatalogHandler.NO_SCHEMA_ID;
            }

            @Override
            public int resolve(
                String subject,
                String version,
                long authorization)
            {
                return authorization == 7L ? 42 : CatalogHandler.NO_SCHEMA_ID;
            }
        };

        assertThat(handler.resolve("subject", "latest", 7L), equalTo(42));
        assertThat(handler.resolve("subject", "latest", 1L), equalTo(CatalogHandler.NO_SCHEMA_ID));
    }
}
