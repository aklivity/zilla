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
package io.aklivity.zilla.runtime.cog.tcp.internal;

import static io.aklivity.zilla.runtime.engine.config.Role.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.Role.SERVER;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.cog.tcp.internal.config.TcpServerBinding;
import io.aklivity.zilla.runtime.cog.tcp.internal.stream.TcpClientFactory;
import io.aklivity.zilla.runtime.cog.tcp.internal.stream.TcpServerFactory;
import io.aklivity.zilla.runtime.cog.tcp.internal.stream.TcpStreamFactory;
import io.aklivity.zilla.runtime.engine.cog.Axle;
import io.aklivity.zilla.runtime.engine.cog.AxleContext;
import io.aklivity.zilla.runtime.engine.cog.stream.StreamFactory;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Role;

final class TcpAxle implements Axle
{
    private final Map<Role, TcpStreamFactory> factories;

    TcpAxle(
        TcpConfiguration config,
        AxleContext context,
        LongFunction<TcpServerBinding> servers)
    {
        Map<Role, TcpStreamFactory> factories = new EnumMap<>(Role.class);
        factories.put(SERVER, new TcpServerFactory(config, context, servers));
        factories.put(CLIENT, new TcpClientFactory(config, context));

        this.factories = factories;
    }

    @Override
    public StreamFactory attach(
        Binding binding)
    {
        TcpStreamFactory tcpFactory = factories.get(binding.kind);
        if (tcpFactory != null)
        {
            assert TcpCog.NAME.equals(binding.type);
            tcpFactory.attach(binding);
        }
        return tcpFactory;
    }

    @Override
    public void detach(
        Binding binding)
    {
        TcpStreamFactory tcpFactory = factories.get(binding.kind);
        if (tcpFactory != null)
        {
            tcpFactory.detach(binding.id);
        }
    }
}
