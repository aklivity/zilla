/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.a2a.internal;

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static io.aklivity.zilla.config.engine.KindConfig.PROXY;
import static io.aklivity.zilla.config.engine.KindConfig.SERVER;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aClientFactory;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aProxyFactory;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aServerFactory;
import io.aklivity.zilla.runtime.binding.a2a.internal.stream.A2aStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

final class A2aBindingContext implements BindingContext
{
    private final Map<KindConfig, A2aStreamFactory> factories;

    A2aBindingContext(
        A2aConfiguration config,
        EngineContext context)
    {
        final Map<KindConfig, A2aStreamFactory> factories = new EnumMap<>(KindConfig.class);
        factories.put(SERVER, new A2aServerFactory(config, context));
        factories.put(CLIENT, new A2aClientFactory(config, context));
        factories.put(PROXY, new A2aProxyFactory(config, context));
        this.factories = factories;
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        final A2aStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.attach(binding);
        }

        return factory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        final A2aStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), factories);
    }
}
