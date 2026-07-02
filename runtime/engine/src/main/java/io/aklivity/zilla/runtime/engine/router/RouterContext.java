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
import io.aklivity.zilla.runtime.engine.config.RouterConfig;

/**
 * Per-thread context for a stream factory composition plugin.
 * <p>
 * Created once per I/O thread by {@link Router#supply(RouteableContext)} and confined to that
 * thread. Manages the lifecycle of {@link BindingHandler} composition for a router configuration
 * active on this thread.
 * </p>
 *
 * @see Router
 */
public interface RouterContext
{
    /**
     * Attaches a router configuration to this thread's context, returning the composed
     * {@link BindingHandler} that will be installed as the engine's stream factory.
     *
     * @param config  the router configuration to activate
     * @return a {@link BindingHandler} that the engine installs as its stream factory
     */
    BindingHandler attach(
        RouterConfig config);

    /**
     * Detaches a previously attached router configuration from this thread's context,
     * releasing any associated resources.
     *
     * @param routerId  the id of the router configuration to deactivate, matching
     *                  {@link RouterConfig#id}
     */
    void detach(
        long routerId);

    /**
     * Resolves the stream affinity for the given binding. A composing router may map the
     * supplied affinity onto a different value; the encoding of the resolved affinity is
     * router-specific. The default resolution returns the affinity unchanged.
     *
     * @param bindingId  the binding id whose affinity to resolve
     * @param affinity   the affinity to resolve
     * @return the resolved affinity
     */
    default long affinity(
        long bindingId,
        long affinity)
    {
        return affinity;
    }

    /**
     * Returns {@code true} if the given affinity is local to this router, {@code false}
     * otherwise. The interpretation of locality is router-specific. The default treats every
     * affinity as local.
     *
     * @param bindingId  the binding id whose affinity to consult
     * @param affinity   the affinity to test
     * @return {@code true} if the affinity is local to this router; otherwise {@code false}
     */
    default boolean isLocalAffinity(
        long bindingId,
        long affinity)
    {
        return true;
    }
}
