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
package io.aklivity.zilla.runtime.store.memory.internal;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.Store;
import io.aklivity.zilla.runtime.engine.store.StoreContext;

final class MemoryStore implements Store
{
    static final String NAME = "memory";

    private final ConcurrentMap<Long, ConcurrentMap<String, MemoryEntry>> entries;

    MemoryStore(
        MemoryStoreConfiguration config)
    {
        this.entries = new ConcurrentHashMap<>();
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public StoreContext supply(
        EngineContext context)
    {
        return new MemoryStoreContext(this);
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/memory.schema.patch.json");
    }

    ConcurrentMap<String, MemoryEntry> attach(
        long storeId)
    {
        return entries.computeIfAbsent(storeId, id -> new ConcurrentHashMap<>());
    }
}
