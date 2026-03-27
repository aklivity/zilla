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
package io.aklivity.zilla.runtime.engine.binding;

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.EngineController;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

/**
 * Entry point for a protocol binding plugin.
 * <p>
 * A {@code Binding} represents a named protocol handler (e.g., {@code http}, {@code kafka}, {@code mqtt})
 * that the engine discovers via the Java {@link java.util.ServiceLoader} mechanism. Each binding is
 * instantiated once per engine and is responsible for creating per-thread {@link BindingContext}
 * instances that handle the actual stream processing.
 * </p>
 * <p>
 * Implementations are registered by placing a {@link BindingFactorySpi} provider in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.binding.BindingFactorySpi}.
 * </p>
 *
 * @see BindingContext
 * @see BindingFactorySpi
 */
public interface Binding
{
    /**
     * Returns the unique name identifying this binding type, e.g. {@code "http"}, {@code "kafka"}.
     * This name must match the {@code type} field used in {@code zilla.yaml} configuration.
     *
     * @return the binding type name
     */
    String name();

    /**
     * Creates a per-thread context for this binding.
     * <p>
     * Called once per I/O thread during engine startup. The returned {@link BindingContext} is
     * confined to that thread and may hold thread-local state without synchronization.
     * </p>
     *
     * @param context  the engine context providing per-thread services (buffer pool, signaler, etc.)
     * @return a new {@link BindingContext} for the calling I/O thread
     */
    BindingContext supply(
        EngineContext context);

    /**
     * Creates a control-plane controller for this binding, if supported.
     * <p>
     * The controller runs on the control thread rather than the I/O threads, and is used for
     * operations such as dynamic attach/detach. Returns {@code null} by default for bindings
     * that have no control-plane operations.
     * </p>
     *
     * @param controller  the engine controller interface
     * @return a {@link BindingController}, or {@code null} if this binding has no control-plane operations
     */
    default BindingController supply(
        EngineController controller)
    {
        return null;
    }

    /**
     * Returns a URL pointing to the JSON schema for this binding's configuration options, or
     * {@code null} if no schema is provided.
     *
     * @return the configuration schema URL, or {@code null}
     */
    default URL type()
    {
        return null;
    }

    /**
     * Returns the origin (inbound) protocol type name for the given binding kind.
     * <p>
     * Used by the engine to wire compatible bindings together — the origin type of a downstream
     * binding must match the routed type of the upstream binding.
     * </p>
     *
     * @param kind  the binding kind (e.g., {@code server}, {@code client}, {@code proxy})
     * @return the origin type name, or {@code null} to accept any origin type
     */
    default String originType(
        KindConfig kind)
    {
        return null;
    }

    /**
     * Returns the routed (outbound) protocol type name for the given binding kind.
     *
     * @param kind  the binding kind
     * @return the routed type name, or {@code null} to emit any routed type
     */
    default String routedType(
        KindConfig kind)
    {
        return null;
    }

    /**
     * Returns the number of I/O worker threads this binding requires for the given kind.
     * <p>
     * Returns {@link Integer#MAX_VALUE} by default, meaning the binding is compatible with
     * any worker count. Override to constrain to a specific count (e.g., {@code 1} for
     * bindings that maintain global ordered state).
     * </p>
     *
     * @param kind  the binding kind
     * @return the required worker count, or {@link Integer#MAX_VALUE} for no constraint
     */
    default int workers(
        KindConfig kind)
    {
        return Integer.MAX_VALUE;
    }
}
