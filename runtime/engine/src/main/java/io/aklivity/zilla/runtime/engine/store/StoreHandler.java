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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides key-value state operations for streams passing through a binding backed by a store.
 * <p>
 * A {@code StoreHandler} is obtained from {@link StoreContext#attach(StoreConfig)} and is
 * confined to a single I/O thread. It maintains mutable runtime state — session tokens, JWKS
 * keys, idempotency records, rate-limit counters, OAuth nonces — and evaluates operations on
 * the hot path without blocking or synchronization.
 * </p>
 * <p>
 * Completion callbacks are pre-allocated per-stream and passed into each operation to avoid
 * heap allocation on the data path.
 * </p>
 *
 * @see StoreContext
 */
public interface StoreHandler
{
    /**
     * Retrieves the value associated with the given key.
     *
     * @param key        the key to look up
     * @param completion a pre-allocated callback that receives {@code (key, value)};
     *                   {@code value} is {@code null} if the key is not present
     */
    void get(
        String key,
        BiConsumer<String, String> completion);

    /**
     * Associates the given value with the given key, replacing any existing value.
     *
     * @param key        the key to store
     * @param value      the value to associate
     * @param ttl        the time-to-live in milliseconds, or {@code Long.MAX_VALUE} for no expiry
     * @param completion a pre-allocated callback invoked when the operation completes
     */
    void put(
        String key,
        String value,
        long ttl,
        Consumer<String> completion);

    /**
     * Associates the given value with the given key only if the key is not already present.
     *
     * @param key        the key to store
     * @param value      the value to associate if absent
     * @param ttl        the time-to-live in milliseconds, or {@code Long.MAX_VALUE} for no expiry
     * @param completion a pre-allocated callback that receives the existing value if present,
     *                   or {@code null} if the key was absent and the value was stored
     */
    void putIfAbsent(
        String key,
        String value,
        long ttl,
        Consumer<String> completion);

    /**
     * Removes the entry for the given key.
     *
     * @param key        the key to remove
     * @param completion a pre-allocated callback invoked when the operation completes
     */
    void delete(
        String key,
        Consumer<String> completion);

    /**
     * Atomically retrieves and removes the entry for the given key.
     *
     * @param key        the key to retrieve and remove
     * @param completion a pre-allocated callback that receives the value that was removed,
     *                   or {@code null} if the key was not present
     */
    void getAndDelete(
        String key,
        Consumer<String> completion);
}
