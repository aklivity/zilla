/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.ws.internal;

import static io.aklivity.zilla.runtime.engine.config.Role.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.Role.SERVER;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.runtime.cog.ws.internal.stream.WsClientFactory;
import io.aklivity.zilla.runtime.cog.ws.internal.stream.WsServerFactory;
import io.aklivity.zilla.runtime.cog.ws.internal.stream.WsStreamFactory;
import io.aklivity.zilla.runtime.engine.cog.Axle;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Role;

final class WsAxle implements Axle
{
    private final Map<Role, WsStreamFactory> factories;

    WsAxle(
        WsConfiguration config,
        AxleContext context)
    {
        final Map<Role, WsStreamFactory> factories = new EnumMap<>(Role.class);
        factories.put(SERVER, new WsServerFactory(config, context));
        factories.put(CLIENT, new WsClientFactory(config, context));
        this.factories = factories;
    }

    @Override
    public StreamFactory attach(
        Binding binding)
    {
        WsStreamFactory factory = factories.get(binding.kind);

        if (factory != null)
        {
            factory.attach(binding);
        }

        return factory;
    }

    @Override
    public void detach(
        Binding binding)
    {
        WsStreamFactory factory = factories.get(binding.kind);

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
