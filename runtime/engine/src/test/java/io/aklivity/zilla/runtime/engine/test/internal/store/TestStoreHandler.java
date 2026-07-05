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
package io.aklivity.zilla.runtime.engine.test.internal.store;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.StoreConfig;
import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class TestStoreHandler implements StoreHandler
{
    private final Map<String, String> entries;
    private final Signaler signaler;

    public TestStoreHandler(
        StoreConfig store,
        Signaler signaler)
    {
        this.entries = new HashMap<>();
        this.signaler = Objects.requireNonNull(signaler);
    }

    @Override
    public void get(
        String key,
        BiConsumer<String, String> completion)
    {
        final String value = entries.get(key);
        defer(() -> completion.accept(key, value));
    }

    @Override
    public void put(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        entries.put(key, value);
        defer(() -> completion.accept(null));
    }

    @Override
    public void putIfAbsent(
        String key,
        String value,
        long ttl,
        Consumer<String> completion)
    {
        final String existing = entries.putIfAbsent(key, value);
        defer(() -> completion.accept(existing));
    }

    @Override
    public void delete(
        String key,
        Consumer<String> completion)
    {
        entries.remove(key);
        defer(() -> completion.accept(null));
    }

    @Override
    public void getAndDelete(
        String key,
        Consumer<String> completion)
    {
        final String prior = entries.remove(key);
        defer(() -> completion.accept(prior));
    }

    private void defer(
        Runnable task)
    {
        // contract: callback fires strictly later than the call, on the caller's I/O thread
        signaler.signalAt(System.currentTimeMillis(), 0, ignored -> task.run());
    }
}
