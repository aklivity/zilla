/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.store.memory.internal;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.Store;

final class MemoryStore implements Store
{
    private final ConcurrentHashMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    @Override
    public String name()
    {
        return MemoryStoreFactorySpi.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/memory.schema.patch.json");
    }

    @Override
    public MemoryStoreContext supply(
        EngineContext context)
    {
        return new MemoryStoreContext(entries);
    }
}
