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
package io.aklivity.zilla.runtime.engine.internal.poller;

import static org.agrona.CloseHelper.quietClose;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;

public final class Poller extends TransportPoller
{
    private final ToIntFunction<SelectionKey> selectHandler;

    public Poller()
    {
        this.selectHandler = this::handleSelect;
    }

    public int doWork()
    {
        int workDone = 0;

        try
        {
            if (selector.selectNow() != 0)
            {
                workDone = selectedKeySet.forEach(selectHandler);
            }
        }
        catch (Throwable ex)
        {
            selectedKeySet.reset();
            LangUtil.rethrowUnchecked(ex);
        }

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

    private static PollerKeyImpl attachment(
        SelectionKey key)
    {
        return (PollerKeyImpl) key.attachment();
    }
}
