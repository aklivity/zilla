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

/**
 * Service provider interface for engine lifecycle extension hooks.
 * <p>
 * An {@code EngineExtSpi} implementation receives callbacks at key points in the engine
 * lifecycle, allowing external components (e.g., management agents, health reporters,
 * integration test harnesses) to observe engine state transitions without coupling to
 * engine internals.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} and must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.ext.EngineExtSpi}.
 * All callbacks have default no-op implementations; override only the events of interest.
 * </p>
 * <p>
 * The callback sequence for a normal engine run is:
 * <ol>
 *   <li>{@link #onStart} — engine threads started, ready to process streams</li>
 *   <li>{@link #onRegistered} — a namespace configuration was successfully loaded</li>
 *   <li>{@link #onUnregistered} — a namespace configuration was unloaded</li>
 *   <li>{@link #onClose} — engine is shutting down</li>
 * </ol>
 * </p>
 *
 * @see EngineExtContext
 */
public interface EngineExtSpi
{
    /**
     * Called once after all engine I/O threads have started and the engine is ready
     * to accept stream connections.
     *
     * @param context  the engine extension context providing configuration and metric access
     */
    default void onStart(
        EngineExtContext context)
    {
    }

    /**
     * Called each time a namespace configuration is successfully registered with the engine,
     * including the initial configuration load at startup and any subsequent live reloads.
     *
     * @param context  the engine extension context
     */
    default void onRegistered(
        EngineExtContext context)
    {
    }

    /**
     * Called each time a namespace configuration is unregistered from the engine,
     * typically during a live configuration reload before the new configuration is registered.
     *
     * @param context  the engine extension context
     */
    default void onUnregistered(
        EngineExtContext context)
    {
    }

    /**
     * Called once when the engine is shutting down, after all I/O threads have been
     * signalled to stop.
     *
     * @param context  the engine extension context
     */
    default void onClose(
        EngineExtContext context)
    {
    }
}
