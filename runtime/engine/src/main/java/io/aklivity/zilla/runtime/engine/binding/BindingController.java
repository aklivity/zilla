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
 * Control-plane interface for a protocol binding.
 * <p>
 * While {@link BindingContext} operates on I/O threads, a {@code BindingController} runs on the
 * engine control thread and handles configuration lifecycle operations that do not need to be
 * replicated per I/O thread. Returned by {@link Binding#supply(EngineController)} for bindings
 * that require control-plane participation.
 * </p>
 *
 * @see Binding
 */
public interface BindingController
{
    /**
     * Notifies the controller that a binding configuration has been attached to the engine.
     * <p>
     * The default implementation does nothing; override to perform control-plane setup when
     * a binding goes live (e.g., registering with an external control system).
     * </p>
     *
     * @param binding  the binding configuration that was attached
     */
    default void attach(
        BindingConfig binding)
    {
    }

    /**
     * Notifies the controller that a binding configuration has been detached from the engine.
     * <p>
     * The default implementation does nothing; override to perform control-plane teardown
     * when a binding is removed.
     * </p>
     *
     * @param binding  the binding configuration that was detached
     */
    default void detach(
        BindingConfig binding)
    {
    }
}
