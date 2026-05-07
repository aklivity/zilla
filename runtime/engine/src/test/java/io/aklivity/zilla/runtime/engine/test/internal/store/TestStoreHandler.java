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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.config.StoreConfig;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class TestStoreHandler implements StoreHandler
{
    private final Map<String, String> entries;

    public TestStoreHandler(
        StoreConfig store)
    {
        this.entries = new HashMap<>();
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        completion.accept(key, entries.get(key));
    }

    @Override
    public void put(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        entries.put(key, value);
        completion.accept(null);
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        String existing = entries.putIfAbsent(key, value);
        completion.accept(existing);
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        entries.remove(key);
        completion.accept(null);
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        completion.accept(entries.remove(key));
    }
}
