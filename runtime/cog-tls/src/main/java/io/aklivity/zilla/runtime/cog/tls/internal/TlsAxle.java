/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.runtime.cog.tls.internal;

import static io.aklivity.zilla.runtime.engine.config.Role.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.Role.PROXY;
import static io.aklivity.zilla.runtime.engine.config.Role.SERVER;

import java.util.EnumMap;
import java.util.Map;

import io.aklivity.zilla.runtime.cog.tls.internal.stream.TlsClientFactory;
import io.aklivity.zilla.runtime.cog.tls.internal.stream.TlsProxyFactory;
import io.aklivity.zilla.runtime.cog.tls.internal.stream.TlsServerFactory;
import io.aklivity.zilla.runtime.cog.tls.internal.stream.TlsStreamFactory;
import io.aklivity.zilla.runtime.engine.cog.Axle;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Role;

final class TlsAxle implements Axle
{
    private final Map<Role, TlsStreamFactory> factories;

    TlsAxle(
        TlsConfiguration config,
        AxleContext context)
    {
        TlsCounters counters = new TlsCounters(context::supplyCounter, context::supplyAccumulator);
        Map<Role, TlsStreamFactory> factories = new EnumMap<>(Role.class);
        factories.put(SERVER, new TlsServerFactory(config, context, counters));
        factories.put(PROXY, new TlsProxyFactory(config, context, counters));
        factories.put(CLIENT, new TlsClientFactory(config, context, counters));
        this.factories = factories;
    }

    @Override
    public StreamFactory attach(
        Binding binding)
    {
        TlsStreamFactory factory = factories.get(binding.kind);
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
        TlsStreamFactory factory = factories.get(binding.kind);
        if (factory != null)
        {
            factory.detach(binding.id);
        }
    }
}
