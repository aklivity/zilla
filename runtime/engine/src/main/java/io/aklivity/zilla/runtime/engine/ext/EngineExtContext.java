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
package io.aklivity.zilla.runtime.engine.ext;

import java.util.function.LongSupplier;

import io.aklivity.zilla.runtime.engine.Configuration;

/**
 * Context supplied to {@link EngineExtSpi} callbacks, providing access to engine
 * configuration and live metric values.
 * <p>
 * Metric suppliers returned by {@link #counter} and {@link #gauge} read directly from the
 * engine's shared memory metric buffers and may be called at any time after the
 * {@link EngineExtSpi#onStart} callback without additional synchronization.
 * </p>
 *
 * @see EngineExtSpi
 */
public interface EngineExtContext
{
    /**
     * Returns the engine's {@link Configuration}, providing access to all configured
     * engine properties.
     *
     * @return the engine configuration
     */
    Configuration config();

    /**
     * Reports a non-fatal error encountered during a configuration reload.
     * <p>
     * Called by the engine when a live configuration update fails, allowing the extension
     * to log or propagate the error. The engine will roll back to the previous configuration
     * after invoking this method.
     * </p>
     *
     * @param error  the exception describing the configuration error
     */
    void onError(
        Exception error);

    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of
     * a counter metric for the named binding.
     *
     * @param namespace  the namespace containing the binding
     * @param binding    the binding name
     * @param metric     the fully-qualified metric name (e.g., {@code "http.request.size"})
     * @return a supplier for the current counter value
     */
    LongSupplier counter(
        String namespace,
        String binding,
        String metric);

    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of
     * a gauge metric for the named binding.
     *
     * @param namespace  the namespace containing the binding
     * @param binding    the binding name
     * @param metric     the fully-qualified metric name
     * @return a supplier for the current gauge value
     */
    LongSupplier gauge(
        String namespace,
        String binding,
        String metric);
}
