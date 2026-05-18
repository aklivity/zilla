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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.engine.store.StoreHandler;

public final class McpLifecycleCache
{
    private static final String STORE_LOCK_KEY = "lifecycle.lock";
    private static final String STORE_LOCK_VALUE = "1";

    private final StoreHandler store;

    public McpLifecycleCache(
        StoreHandler store)
    {
        this.store = store;
    }

    public void acquireLifecycle(
        long ttl,
        Consumer<Boolean> completion)
    {
        store.putIfAbsent(STORE_LOCK_KEY, STORE_LOCK_VALUE, ttl,
            prior -> completion.accept(prior == null));
    }

    public void releaseLifecycle(
        Consumer<String> completion)
    {
        store.delete(STORE_LOCK_KEY, completion);
    }
}
