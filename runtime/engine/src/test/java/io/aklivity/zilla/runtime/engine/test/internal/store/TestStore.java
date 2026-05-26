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
package io.aklivity.zilla.runtime.engine.test.internal.store;

import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.store.Store;

public final class TestStore implements Store
{
    public static final String NAME = "test";

    private final ConcurrentMap<Long, ConcurrentMap<String, String>> storage;
    private final ConcurrentMap<Long, ConcurrentMap<String, List<TestWatcher>>> storageListeners;
    private final ConcurrentMap<Long, ConcurrentMap<String, TestLockEntry>> storageLocks;

    public TestStore(
        Configuration config)
    {
        this.storage = new ConcurrentHashMap<>();
        this.storageListeners = new ConcurrentHashMap<>();
        this.storageLocks = new ConcurrentHashMap<>();
    }

    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("test.schema.patch.json");
    }

    @Override
    public TestStoreContext supply(
        EngineContext context)
    {
        return new TestStoreContext(context, this::acquireEntries, this::acquireListeners, this::acquireLocks);
    }

    private ConcurrentMap<String, String> acquireEntries(
        long storeId)
    {
        return storage.computeIfAbsent(storeId, id -> new ConcurrentHashMap<>());
    }

    private ConcurrentMap<String, List<TestWatcher>> acquireListeners(
        long storeId)
    {
        return storageListeners.computeIfAbsent(storeId, id -> new ConcurrentHashMap<>());
    }

    private ConcurrentMap<String, TestLockEntry> acquireLocks(
        long storeId)
    {
        return storageLocks.computeIfAbsent(storeId, id -> new ConcurrentHashMap<>());
    }
}
