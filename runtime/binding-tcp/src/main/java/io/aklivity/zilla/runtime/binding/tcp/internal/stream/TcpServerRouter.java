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
package io.aklivity.zilla.runtime.binding.tcp.internal.stream;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.nio.channels.SelectionKey.OP_ACCEPT;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.agrona.CloseHelper;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.tcp.internal.TcpCapacityTracker;
import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpBindingConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpServerBindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

public final class TcpServerRouter
{
    private final Long2ObjectHashMap<TcpBindingConfig> bindings;
    private final ToIntFunction<PollerKey> acceptHandler;
    private final Function<SelectableChannel, PollerKey> supplyPollerKey;
    private final Long2ObjectHashMap<TcpServerBindingConfig> serversById;
    private final TcpCapacityTracker capacity;

    private boolean unbound;

    public TcpServerRouter(
        EngineContext context,
        ToIntFunction<PollerKey> acceptHandler,
        TcpCapacityTracker capacity)
    {
        this.bindings = new Long2ObjectHashMap<>();
        this.supplyPollerKey = context::supplyPollerKey;
        this.acceptHandler = acceptHandler;
        this.serversById = new Long2ObjectHashMap<>();
        this.capacity = capacity;
    }

    public void attach(
        TcpBindingConfig binding)
    {
        bindings.put(binding.id, binding);

        register(binding);
    }

    public TcpBindingConfig resolve(
        long bindingId,
        long authorization)
    {
        return bindings.get(bindingId);
    }

    public void detach(
        long bindingId)
    {
        TcpBindingConfig binding = bindings.remove(bindingId);
        unregister(binding);
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), bindings);
    }

    public SocketChannel accept(
        ServerSocketChannel server) throws IOException
    {
        SocketChannel channel = null;

        if (capacity.available() > 0)
        {
            channel = server.accept();

            if (channel != null)
            {
                capacity.claim();
            }
        }

        if (!unbound && capacity.available() <= 0)
        {
            bindings.values().stream()
                .filter(b -> b.kind == SERVER)
                .forEach(this::unregister);
            unbound = true;
        }

        return channel;
    }

    public void close(
        SocketChannel channel)
    {
        CloseHelper.quietClose(channel);
        capacity.released();

        if (unbound && capacity.available() > 0)
        {
            bindings.values().stream()
                .filter(b -> b.kind == SERVER)
                .forEach(this::register);
            unbound = false;
        }
    }

    private void register(
        TcpBindingConfig binding)
    {
        TcpServerBindingConfig server = serversById.computeIfAbsent(binding.id, TcpServerBindingConfig::new);
        ServerSocketChannel[] channels = server.bind(binding.options);

        PollerKey[] acceptKeys = new PollerKey[channels.length];
        for (int i = 0; i < channels.length; i++)
        {
            acceptKeys[i] = supplyPollerKey.apply(channels[i]);
            acceptKeys[i].handler(OP_ACCEPT, acceptHandler);
            acceptKeys[i].register(OP_ACCEPT);

            acceptKeys[i].attach(binding);
        }

        binding.attach(acceptKeys);
    }

    private void unregister(
        TcpBindingConfig binding)
    {
        PollerKey[] acceptKeys = binding.attach(null);
        if (acceptKeys != null)
        {
            for (PollerKey acceptKey : acceptKeys)
            {
                acceptKey.cancel();
            }
        }

        TcpServerBindingConfig server = serversById.get(binding.id);
        server.unbind();
    }
}
