/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.pgsql.internal;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.pgsql.internal.stream.PgsqlClientFactory;
import io.aklivity.zilla.runtime.binding.pgsql.internal.stream.PgsqlServerFactory;
import io.aklivity.zilla.runtime.binding.pgsql.internal.stream.PgsqlStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;

final class PgsqlBindingContext implements BindingContext
{
    private final Map<KindConfig, PgsqlStreamFactory> factories;

    PgsqlBindingContext(
        PgsqlConfiguration config,
        EngineContext context)
    {
        final EnumMap<KindConfig, PgsqlStreamFactory> factories = new EnumMap<>(KindConfig.class);
        factories.put(SERVER, new PgsqlServerFactory(config, context));
        factories.put(CLIENT, new PgsqlClientFactory(config, context));
        this.factories = factories;
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        PgsqlStreamFactory factory = factories.get(binding.kind);

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
        PgsqlStreamFactory factory = factories.get(binding.kind);

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
