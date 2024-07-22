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
package io.aklivity.zilla.runtime.catalog.schema.registry.internal.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SchemaRegistryCache
{
    public final ConcurrentMap<Integer, CompletableFuture<CachedSchema>> schemas;
    public final ConcurrentMap<Integer, CompletableFuture<CachedSchemaId>> schemaIds;

    public SchemaRegistryCache()
    {
        this.schemas = new ConcurrentHashMap<>();
        this.schemaIds = new ConcurrentHashMap<>();
    }
}
