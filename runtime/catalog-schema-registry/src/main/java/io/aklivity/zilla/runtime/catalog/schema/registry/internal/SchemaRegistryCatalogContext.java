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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;

public class SchemaRegistryCatalogContext implements CatalogContext
{
    private final Long2ObjectHashMap<SchemaRegistryCatalogHandler> handlersById;

    public SchemaRegistryCatalogContext()
    {
        this.handlersById = new Long2ObjectHashMap<>();
    }

    @Override
    public CatalogHandler attach(
        CatalogConfig catalog)
    {
        return new SchemaRegistryCatalogHandler(catalog);
    }

    @Override
    public void detach(
        CatalogConfig catalog)
    {

    }
}
