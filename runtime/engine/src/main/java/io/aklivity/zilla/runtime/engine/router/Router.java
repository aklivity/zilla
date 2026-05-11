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
package io.aklivity.zilla.runtime.engine.router;

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
}
