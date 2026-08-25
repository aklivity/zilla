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
package io.aklivity.zilla.runtime.engine.router;

import io.aklivity.zilla.runtime.common.lang.util.function.ObjectIntBiConsumer;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

/**
 * Entry point for a stream factory composition plugin.
 * <p>
 * A {@code Router} contributes to the engine's {@link BindingHandler} stream factory,
 * either by wrapping the engine-supplied default or by replacing it with an alternative
 * dispatch behavior. The resulting handler becomes the value returned by the engine's
 * stream factory accessor.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link RouterFactorySpi}.
 * The router is selected at engine startup by name from engine {@link io.aklivity.zilla.runtime.engine.Configuration}.
 * </p>
 *
 * @see RouterContext
 * @see RouteableContext
 * @see RouterFactorySpi
 */
public interface Router
{
    /**
     * Returns the unique name identifying this router type, e.g. {@code "noop"}.
     *
     * @return the router type name
     */
    String name();

    /**
     * Creates a per-thread context for this router.
     * <p>
     * Called once per I/O thread. The returned {@link RouterContext} is confined to that thread.
     * The supplied {@link RouteableContext} exposes the engine's current default stream factory
     * and namespace composition primitives that the router may use during its setup.
     * </p>
     *
     * @param context  the under-engine context for the calling I/O thread
     * @return a new {@link RouterContext}
     */
    RouterContext supply(
        RouteableContext context);

    /**
     * Resolves a label to its integer label id, registering the label if it has not
     * been seen before.
     *
     * @param label  the label
     * @return the corresponding integer label id
     */
    int supplyLabelId(
        String label);

    /**
     * Resolves an integer label id back to its label.
     *
     * @param labelId  the label id
     * @return the corresponding label
     */
    String supplyLabel(
        int labelId);

    /**
     * Registers a listener invoked whenever a new label is registered via
     * {@link #supplyLabelId(String)}, anywhere this router is active.
     *
     * @param listener  invoked with the newly registered label and its assigned id
     */
    void watchLabels(
        ObjectIntBiConsumer<String> listener);

    /**
     * Supplies this engine instance's own node identity, embedded in the affinity value
     * each worker mints for itself and consulted whenever an affinity's node component is
     * compared against this instance's own.
     * <p>
     * Identity is a single byte, shared cluster-wide; a router coordinating membership
     * across instances is responsible for assigning distinct values from the given,
     * engine-supplied {@code instanceId} -- a stable per-instance label the caller owns
     * resolving, persisting, and passing in, so a router implementation never needs its
     * own access to engine configuration to obtain one.
     * </p>
     *
     * @param instanceId  this engine instance's own stable, persistent identity label
     * @return this instance's own node identity
     */
    byte supplyNodeId(
        String instanceId);
}
