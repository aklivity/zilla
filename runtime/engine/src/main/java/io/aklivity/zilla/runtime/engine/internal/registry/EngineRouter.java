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
package io.aklivity.zilla.runtime.engine.internal.registry;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.config.RouterConfig;
import io.aklivity.zilla.runtime.engine.router.RouteableContext;
import io.aklivity.zilla.runtime.engine.router.Router;
import io.aklivity.zilla.runtime.engine.router.RouterContext;

// Per-worker BindingHandler that EngineWorker exposes via streamFactory() and binding factories cache
// at supply time. Engine constructs one EngineRouter per worker. The owning worker provides its default
// stream factory and routeable context via attach(...) before binding.supply, so the cached reference
// already dispatches correctly. start() (called from EngineWorker.onStart) swaps the internal delegate
// to the router-wrapped factory; close() (from onClose) restores the default and detaches.
public final class EngineRouter implements BindingHandler
{
    private final Router router;
    private final RouterConfig config;

    private BindingHandler delegate;
    private BindingHandler defaultDelegate;
    private RouteableContext routeable;
    private RouterContext context;

    public EngineRouter(
        Router router,
        RouterConfig config)
    {
        this.router = router;
        this.config = config;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        return delegate.newStream(msgTypeId, buffer, index, length, sender);
    }

    void attach(
        BindingHandler defaultStreamFactory,
        RouteableContext routeable)
    {
        this.defaultDelegate = defaultStreamFactory;
        this.delegate = defaultStreamFactory;
        this.routeable = routeable;
    }

    void start()
    {
        if (router != null && context == null)
        {
            context = router.supply(routeable);
            delegate = context.attach(config);
        }
    }

    void close()
    {
        if (context != null)
        {
            context.detach(config.id);
            context = null;
            delegate = defaultDelegate;
        }
    }
}
