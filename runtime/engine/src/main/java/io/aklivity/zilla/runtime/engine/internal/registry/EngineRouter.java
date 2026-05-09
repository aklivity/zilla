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

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.RouterConfig;
import io.aklivity.zilla.runtime.engine.router.RouteableContext;
import io.aklivity.zilla.runtime.engine.router.Router;
import io.aklivity.zilla.runtime.engine.router.RouterContext;
import io.aklivity.zilla.runtime.engine.router.RouterFactory;

public final class EngineRouter
{
    private final Router router;
    private final RouterConfig config;
    private final RouterContext context;
    private final BindingHandler streamFactory;

    public EngineRouter(
        EngineConfiguration config)
    {
        String routerName = config.router();
        this.router = routerName != null
            ? RouterFactory.instantiate().create(routerName, config)
            : null;
        this.config = routerName != null
            ? RouterConfig.builder()
                .id(0L)
                .name(routerName)
                .build()
            : null;
        this.context = null;
        this.streamFactory = null;
    }

    public EngineRouter(
        RouterContext context,
        BindingHandler streamFactory)
    {
        this.context = context;
        this.streamFactory = streamFactory;
        this.router = null;
        this.config = null;
    }

    public RouterConfig config()
    {
        return config;
    }

    EngineRouter supplyContext(
        RouteableContext routeable)
    {
        RouterContext context = router != null ? router.supply(routeable) : null;
        return new EngineRouter(context, routeable.streamFactory());
    }

    BindingHandler attach(
        RouterConfig config)
    {
        return context != null ? context.attach(config) : streamFactory;
    }

    void detach(
        RouterConfig config)
    {
        if (context != null)
        {
            context.detach(config.id);
        }
    }
}
