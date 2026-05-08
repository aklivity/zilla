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

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.router.RouteableContext;

final class EngineRouteableContext implements RouteableContext
{
    private final Configuration config;
    private final BindingHandler streamFactory;
    private final EngineContext engineContext;

    EngineRouteableContext(
        Configuration config,
        BindingHandler streamFactory,
        EngineContext engineContext)
    {
        this.config = config;
        this.streamFactory = streamFactory;
        this.engineContext = engineContext;
    }

    @Override
    public Configuration config()
    {
        return config;
    }

    @Override
    public BindingHandler streamFactory()
    {
        return streamFactory;
    }

    @Override
    public void attachComposite(
        NamespaceConfig composite)
    {
        engineContext.attachComposite(composite);
    }

    @Override
    public void detachComposite(
        NamespaceConfig composite)
    {
        engineContext.detachComposite(composite);
    }
}
