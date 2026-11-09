/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import java.time.Instant;
import java.util.function.IntConsumer;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;

final class ManualSignaler implements Signaler
{
    long timeMillis;
    IntConsumer handler;

    void fire()
    {
        final IntConsumer fired = handler;
        handler = null;
        fired.accept(1);
    }

    @Override
    public long signalAt(
        long timeMillis,
        int signalId,
        IntConsumer handler)
    {
        this.timeMillis = timeMillis;
        this.handler = handler;
        return 1L;
    }

    @Override
    public long signalAt(
        Instant time,
        int signalId,
        IntConsumer handler)
    {
        return signalAt(time.toEpochMilli(), signalId, handler);
    }

    @Override
    public void signalNow(
        long originId,
        long routedId,
        long streamId,
        long traceId,
        int signalId,
        int contextId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void signalNow(
        long originId,
        long routedId,
        long streamId,
        long traceId,
        int signalId,
        int contextId,
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long signalAt(
        long timeMillis,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        int signalId,
        int contextId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long signalAt(
        Instant time,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        int signalId,
        int contextId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long signalTask(
        Runnable task,
        long originId,
        long routedId,
        long streamId,
        long traceId,
        int signalId,
        int contextId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(
        long cancelId)
    {
        throw new UnsupportedOperationException();
    }
}
