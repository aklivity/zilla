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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static java.net.StandardSocketOptions.SO_KEEPALIVE;
import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.net.StandardSocketOptions.SO_REUSEPORT;
import static java.net.StandardSocketOptions.TCP_NODELAY;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static org.agrona.CloseHelper.quietCloseAll;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Random;

import org.agrona.LangUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.engine.EngineController;
import io.aklivity.zilla.runtime.engine.binding.BindingController;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;

final class TcpBindingController implements BindingController
{
    private final TcpConfiguration config;
    private final EngineController controller;
    private final Int2ObjectHashMap<TcpBindingContext> contexts;
    private final Long2ObjectHashMap<ServerSocketChannel[]> serversById;
    private final Random random;

    TcpBindingController(
        TcpConfiguration config,
        EngineController controller,
        Int2ObjectHashMap<TcpBindingContext> contexts)
    {
        this.config = config;
        this.controller = controller;
        this.contexts = contexts;
        this.serversById = new Long2ObjectHashMap<>();
        this.random = new Random();
    }

    @Override
    public void attach(
        BindingConfig binding)
    {
        try
        {
            TcpOptionsConfig options = (TcpOptionsConfig) binding.options;
            int size = options.ports != null ? options.ports.length : 0;
            ServerSocketChannel[] channels = new ServerSocketChannel[size];

            for (int i = 0; i < size; i++)
            {
                ServerSocketChannel channel = ServerSocketChannel.open();

                InetAddress address = InetAddress.getByName(options.host);
                InetSocketAddress local = new InetSocketAddress(address, options.ports[i]);

                channel.setOption(SO_REUSEADDR, true);
                channel.setOption(SO_REUSEPORT, true);
                channel.bind(local, options.backlog);
                channel.configureBlocking(false);

                channels[i] = channel;
            }

            PollerKey[] acceptKeys = new PollerKey[channels.length];
            for (int i = 0; i < channels.length; i++)
            {
                acceptKeys[i] = controller.supplyPollerKey(channels[i]);
                acceptKeys[i].handler(OP_ACCEPT, this::handleAccept);
                acceptKeys[i].register(OP_ACCEPT);

                acceptKeys[i].attach(binding);
            }

            serversById.put(binding.id, channels);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        ServerSocketChannel[] servers = serversById.remove(binding.id);
        quietCloseAll(servers);
    }

    private int handleAccept(
        PollerKey key)
    {
        try
        {
            BindingConfig binding = (BindingConfig) key.attachment();
            TcpOptionsConfig options = (TcpOptionsConfig) binding.options;
            ServerSocketChannel server = (ServerSocketChannel) key.channel();

            for (SocketChannel channel = server.accept(); channel != null; channel = server.accept())
            {
                channel.configureBlocking(false);
                channel.setOption(TCP_NODELAY, options.nodelay);
                channel.setOption(SO_KEEPALIVE, options.keepalive);

                InetSocketAddress local = (InetSocketAddress) channel.getLocalAddress();

                //TODO: apply load balancing logic
                int index = random.nextInt(contexts.size());
                TcpBindingContext context = contexts.get(index);

                context.onAccepted(binding.id, channel, local);
            }
        }
        catch (IOException ex)
        {
            //TODO: Report error
        }

        return 1;
    }
}
