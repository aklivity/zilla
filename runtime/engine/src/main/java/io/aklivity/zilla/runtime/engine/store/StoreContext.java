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

import io.aklivity.zilla.runtime.engine.config.StoreConfig;

/**
 * Per-thread context for a mutable runtime state store.
 * <p>
 * Created once per I/O thread by {@link Store#supply(EngineContext)} and confined to that thread.
 * Manages the lifecycle of {@link StoreHandler} instances for individual store configurations
 * active on this thread.
 * </p>
 *
 * @see Store
 * @see StoreHandler
 */
public interface StoreContext
{
    /**
     * Attaches a store configuration to this thread's context.
     *
     * @param store  the store configuration to activate
     * @return a {@link StoreHandler} for reading and writing state under this configuration
     */
    StoreHandler attach(
        StoreConfig store);

    /**
     * Detaches a previously attached store configuration from this thread's context,
     * releasing any associated resources.
     *
     * @param store  the store configuration to deactivate
     */
    void detach(
        StoreConfig store);
}
