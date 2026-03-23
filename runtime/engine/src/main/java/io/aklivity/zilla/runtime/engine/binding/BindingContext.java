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

import io.aklivity.zilla.runtime.engine.config.BindingConfig;

/**
 * Per-thread data-plane context for a protocol binding.
 * <p>
 * Each I/O thread receives its own {@code BindingContext} instance, created by
 * {@link Binding#supply(EngineContext)}. Because a {@code BindingContext} is confined to a single
 * thread, all state it holds can be read and written without synchronization.
 * </p>
 * <p>
 * When a binding configuration is loaded, the engine calls {@link #attach(BindingConfig)} on every
 * thread's context. The returned {@link BindingHandler} is used by that thread to create new
 * streams routed through the binding.
 * </p>
 *
 * @see Binding
 * @see BindingHandler
 */
public interface BindingContext
{
    /**
     * Attaches a binding configuration to this thread's context.
     * <p>
     * Called when a binding is added to the engine namespace. The returned {@link BindingHandler}
     * handles all new inbound streams for this binding on this I/O thread.
     * </p>
     *
     * @param binding  the binding configuration to attach
     * @return a {@link BindingHandler} for this binding, or {@code null} if this binding
     *         does not accept inbound streams
     */
    default BindingHandler attach(
        BindingConfig binding)
    {
        return null;
    }

    /**
     * Detaches a previously attached binding configuration from this thread's context.
     * <p>
     * Called when a binding is removed from the engine namespace. Implementations should
     * release any per-binding resources allocated during {@link #attach(BindingConfig)}.
     * </p>
     *
     * @param binding  the binding configuration to detach
     */
    default void detach(
        BindingConfig binding)
    {
    }
}
