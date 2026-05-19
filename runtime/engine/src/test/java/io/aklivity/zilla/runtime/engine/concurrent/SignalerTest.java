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
package io.aklivity.zilla.runtime.engine.concurrent;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.function.IntConsumer;

import org.agrona.DirectBuffer;
import org.junit.Test;

public class SignalerTest
{
    @Test
    public void shouldDelegateSimpleSignalAtInstantToEpochMilli()
    {
        final long[] captured = new long[1];
        final Signaler signaler = new TestSignaler()
        {
            @Override
            public long signalAt(
                long timeMillis,
                int signalId,
                IntConsumer handler)
            {
                captured[0] = timeMillis;
                return 42L;
            }
        };

        final Instant time = Instant.ofEpochMilli(1_700_000_000_000L);
        final long cancelId = signaler.signalAt(time, 7, sig -> {});

        assertEquals(1_700_000_000_000L, captured[0]);
        assertEquals(42L, cancelId);
    }

    @Test
    public void shouldDelegateStreamSignalAtInstantToEpochMilli()
    {
        final long[] captured = new long[1];
        final Signaler signaler = new TestSignaler()
        {
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
                captured[0] = timeMillis;
                return 99L;
            }
        };

        final Instant time = Instant.ofEpochMilli(1_700_000_000_000L);
        final long cancelId = signaler.signalAt(time, 1L, 2L, 3L, 4L, 5, 6);

        assertEquals(1_700_000_000_000L, captured[0]);
        assertEquals(99L, cancelId);
    }

    private abstract static class TestSignaler implements Signaler
    {
        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            return NO_CANCEL_ID;
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
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
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
            return NO_CANCEL_ID;
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
            return NO_CANCEL_ID;
        }

        @Override
        public boolean cancel(
            long cancelId)
        {
            return false;
        }
    }
}
