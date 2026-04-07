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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

final class MemoryStoreHandler implements StoreHandler
{
    private final MemoryStore store;

    MemoryStoreHandler(
        MemoryStore store)
    {
        this.store = store;
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final String value = store.get(key);
        completion.accept(key, value);
    }

    @Override
    public void put(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        store.put(key, value, ttl);
        completion.accept(null);
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        final String existing = store.putIfAbsent(key, value, ttl);
        completion.accept(existing);
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        store.delete(key);
        completion.accept(null);
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        final String value = store.getAndDelete(key);
        completion.accept(value);
    }
}
