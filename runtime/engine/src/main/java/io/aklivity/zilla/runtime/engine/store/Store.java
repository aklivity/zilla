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
package io.aklivity.zilla.runtime.engine.store;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Entry point for a mutable runtime state store plugin.
 * <p>
 * A {@code Store} holds mutable runtime state — session tokens, JWKS keys, parsed specs,
 * idempotency records, rate-limit counters, OAuth nonces — written and read during request
 * processing. The store is accessed synchronously on the I/O thread via the
 * {@link StoreHandler} returned from its {@link StoreContext}.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link StoreFactorySpi}.
 * </p>
 *
 * @see StoreContext
 * @see StoreHandler
 * @see StoreFactorySpi
 */
public interface Store
{
    /**
     * Returns the unique name identifying this store type, e.g. {@code "memory"}.
     *
     * @return the store type name
     */
    String name();

    /**
     * Creates a per-thread context for this store.
     * <p>
     * Called once per I/O thread. The returned {@link StoreContext} is confined to that thread
     * and may hold thread-local state without synchronization.
     * </p>
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link StoreContext}
     */
    StoreContext supply(
        EngineContext context);

    /**
     * Returns a URL pointing to the JSON schema for this store's configuration options.
     *
     * @return the configuration schema URL
     */
    URL type();
}
