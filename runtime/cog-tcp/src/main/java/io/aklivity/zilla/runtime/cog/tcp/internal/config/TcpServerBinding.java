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
package io.aklivity.zilla.runtime.cog.tcp.internal.config;

import static java.net.StandardSocketOptions.SO_REUSEADDR;
import static java.net.StandardSocketOptions.SO_REUSEPORT;
import static org.agrona.CloseHelper.quietClose;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.agrona.LangUtil;

public final class TcpServerBinding
{
    public final long routeId;

    private final Lock lock = new ReentrantLock();
    private final AtomicInteger binds;
    private volatile ServerSocketChannel[] channels;

    public TcpServerBinding(
        long routeId)
    {
        this.routeId = routeId;
        this.binds = new AtomicInteger();
    }

    public ServerSocketChannel[] bind(
        TcpOptions options)
    {
        try
        {
            lock.lock();

            if (binds.getAndIncrement() == 0L)
            {
                assert channels == null;

                int size = options.ports != null ? options.ports.length : 0;
                channels = new ServerSocketChannel[size];

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
            }
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        finally
        {
            lock.unlock();
        }

        return channels;
    }

    public void unbind()
    {
        try
        {
            lock.lock();

            if (binds.decrementAndGet() == 0L)
            {
                assert channels != null;
                for (ServerSocketChannel channel : channels)
                {
                    quietClose(channel);
                }
                channels = null;
            }
        }
        finally
        {
            lock.unlock();
        }
    }
}
