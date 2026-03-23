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
package io.aklivity.zilla.runtime.engine.guard;

import io.aklivity.zilla.runtime.engine.config.GuardConfig;

/**
 * Per-thread context for an authorization guard.
 * <p>
 * Created once per I/O thread by {@link Guard#supply(EngineContext)} and confined to that thread.
 * Manages the lifecycle of {@link GuardHandler} instances for individual guard configurations
 * active on this thread.
 * </p>
 *
 * @see Guard
 * @see GuardHandler
 */
public interface GuardContext
{
    /**
     * Attaches a guard configuration to this thread's context.
     *
     * @param guard  the guard configuration to activate
     * @return a {@link GuardHandler} for verifying and managing sessions under this configuration
     */
    GuardHandler attach(
        GuardConfig guard);

    /**
     * Detaches a previously attached guard configuration from this thread's context,
     * releasing any associated resources.
     *
     * @param guard  the guard configuration to deactivate
     */
    void detach(
        GuardConfig guard);
}
