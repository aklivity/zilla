/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.http.internal.stream.HttpClientFactory;
import io.aklivity.zilla.runtime.binding.http.internal.stream.HttpServerFactory;
import io.aklivity.zilla.runtime.binding.http.internal.stream.HttpStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

final class HttpBindingContext implements BindingContext
{
    private final Map<KindConfig, HttpStreamFactory> factories;

    HttpBindingContext(
        HttpConfiguration config,
        EngineContext context)
    {
        Map<KindConfig, HttpStreamFactory> factories = new EnumMap<>(KindConfig.class);
        factories.put(CLIENT, new HttpClientFactory(config, context));
        factories.put(SERVER, new HttpServerFactory(config, context));
        this.factories = factories;
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        HttpStreamFactory factory = factories.get(binding.kind);

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
        HttpStreamFactory factory = factories.get(binding.kind);

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
