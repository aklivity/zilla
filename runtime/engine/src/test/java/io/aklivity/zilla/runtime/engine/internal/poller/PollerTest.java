/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.poller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PollerTest
{
    private static final int SWEEP_LIMIT = 1024;

    private final AtomicLong bound = new AtomicLong();
    private final AtomicLong unbound = new AtomicLong();

    private Poller poller;

    @Before
    public void initPoller()
    {
        poller = new Poller(bound::addAndGet, unbound::addAndGet);
    }

    @After
    public void closePoller()
    {
        poller.onClose();
    }

    @Test
    public void shouldCountBoundListener() throws Exception
    {
        try (ServerSocketChannel listener = listener())
        {
            poller.register(listener);

            assertThat(bound.get(), equalTo(1L));
            assertThat(unbound.get(), equalTo(0L));
        }
    }

    @Test
    public void shouldCountListenerOnceWhenRegisteredAgain() throws Exception
    {
        try (ServerSocketChannel listener = listener())
        {
            poller.register(listener);
            poller.register(listener);

            assertThat(bound.get(), equalTo(1L));
        }
    }

    @Test
    public void shouldNotCountClientChannel() throws Exception
    {
        try (SocketChannel client = SocketChannel.open())
        {
            client.configureBlocking(false);
            poller.register(client);

            assertThat(bound.get(), equalTo(0L));
        }
    }

    @Test
    public void shouldCountBoundDatagramChannel() throws Exception
    {
        try (DatagramChannel datagram = DatagramChannel.open())
        {
            datagram.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            datagram.configureBlocking(false);
            poller.register(datagram);

            assertThat(bound.get(), equalTo(1L));
        }
    }

    @Test
    public void shouldNotCountUnboundDatagramChannel() throws Exception
    {
        try (DatagramChannel datagram = DatagramChannel.open())
        {
            datagram.configureBlocking(false);
            poller.register(datagram);

            assertThat(bound.get(), equalTo(0L));
        }
    }

    @Test
    public void shouldNotCountConnectedDatagramChannel() throws Exception
    {
        try (DatagramChannel peer = DatagramChannel.open();
            DatagramChannel datagram = DatagramChannel.open())
        {
            peer.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            datagram.connect(peer.getLocalAddress());
            datagram.configureBlocking(false);
            poller.register(datagram);

            assertThat(bound.get(), equalTo(0L));
        }
    }

    @Test
    public void shouldCountUnboundListenerWhenClosed() throws Exception
    {
        ServerSocketChannel listener = listener();
        poller.register(listener);

        listener.close();
        awaitUnbound(1L);

        assertThat(bound.get(), equalTo(1L));
        assertThat(unbound.get(), equalTo(1L));
    }

    @Test
    public void shouldCountUnboundListenerOnceWhenCancelled() throws Exception
    {
        try (ServerSocketChannel listener = listener())
        {
            PollerKeyImpl key = poller.register(listener);

            key.cancel();
            awaitUnbound(1L);
            for (int cycle = 0; cycle < SWEEP_LIMIT; cycle++)
            {
                poller.doWork();
            }

            assertThat(unbound.get(), equalTo(1L));
        }
    }

    @Test
    public void shouldCountBindAfterUnbind() throws Exception
    {
        ServerSocketChannel first = listener();
        poller.register(first);
        first.close();
        awaitUnbound(1L);

        try (ServerSocketChannel second = listener())
        {
            poller.register(second);

            assertThat(bound.get(), equalTo(2L));
            assertThat(unbound.get(), equalTo(1L));
        }
    }

    private void awaitUnbound(
        long expected)
    {
        for (int cycle = 0; cycle < SWEEP_LIMIT && unbound.get() < expected; cycle++)
        {
            poller.doWork();
        }
    }

    private static ServerSocketChannel listener() throws IOException
    {
        ServerSocketChannel listener = ServerSocketChannel.open();
        listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        listener.configureBlocking(false);
        return listener;
    }
}
