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

import static org.agrona.CloseHelper.quietClose;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;

public final class Poller extends TransportPoller
{
    private static final int LISTENER_SWEEP_MASK = 63;

    private final Consumer<SelectionKey> selectHandler;
    private final LongConsumer listenerBound;
    private final LongConsumer listenerUnbound;
    private final List<SelectionKey> listeners;

    private int listenerSweepCycles;

    public Poller()
    {
        this(value -> {}, value -> {});
    }

    public Poller(
        LongConsumer listenerBound,
        LongConsumer listenerUnbound)
    {
        this.selectHandler = this::handleSelect;
        this.listenerBound = listenerBound;
        this.listenerUnbound = listenerUnbound;
        this.listeners = new ArrayList<>();
    }

    public int doWork()
    {
        int workDone = 0;

        try
        {
            workDone += selector.selectNow(selectHandler);
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        sweepListeners();

        return workDone;
    }

    public int doWork(
        int timeout)
    {
        int workDone = 0;

        try
        {
            workDone += selector.select(selectHandler, timeout);
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        sweepListeners();

        return workDone;
    }

    public void onClose()
    {
        for (SelectionKey key : selector.keys())
        {
            quietClose(key.channel());
        }

        // Allow proper cleanup on platforms like Windows
        selectNowWithoutProcessing();

        super.close();
    }

    public PollerKeyImpl register(
        SelectableChannel channel)
    {
        PollerKeyImpl pollerKey = null;

        try
        {
            SelectionKey key = channel.keyFor(selector);
            if (key == null)
            {
                key = channel.register(selector, 0, null);
                key.attach(new PollerKeyImpl(key));

                if (isListener(channel))
                {
                    listeners.add(key);
                    listenerBound.accept(1L);
                }
            }

            pollerKey = attachment(key);
        }
        catch (ClosedChannelException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return pollerKey;
    }

    public Stream<PollerKeyImpl> keys()
    {
        return selector.keys().stream().map(k -> attachment(k));
    }

    private int handleSelect(
        SelectionKey key)
    {
        final PollerKeyImpl attachment = attachment(key);
        return attachment.handleSelect(key);
    }

    private void sweepListeners()
    {
        if (!listeners.isEmpty() && (++listenerSweepCycles & LISTENER_SWEEP_MASK) == 0)
        {
            for (int index = listeners.size() - 1; index >= 0; index--)
            {
                if (!listeners.get(index).isValid())
                {
                    listeners.remove(index);
                    listenerUnbound.accept(1L);
                }
            }
        }
    }

    private static boolean isListener(
        SelectableChannel channel)
    {
        boolean listener = channel instanceof ServerSocketChannel;

        if (channel instanceof DatagramChannel datagram)
        {
            try
            {
                listener = !datagram.isConnected() && datagram.getLocalAddress() != null;
            }
            catch (IOException ex)
            {
                listener = false;
            }
        }

        return listener;
    }

    private static PollerKeyImpl attachment(
        SelectionKey key)
    {
        return (PollerKeyImpl) key.attachment();
    }
}
