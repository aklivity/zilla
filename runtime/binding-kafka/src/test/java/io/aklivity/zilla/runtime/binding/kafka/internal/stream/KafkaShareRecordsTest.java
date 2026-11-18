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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaShareRecord.STATE_DELIVERED;
import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaShareRecord.STATE_DELIVERING;
import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaShareRecord.STATE_READY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaShareRecord.STATE_STAGED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class KafkaShareRecordsTest
{
    private final KafkaShareRecords records = new KafkaShareRecords();

    @Test
    public void shouldStageRecordsUntilAcquired()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 100);
        final KafkaShareRecord second = stage(0, 6, 2, 50);

        assertEquals(2, records.stagedCount());
        assertSame(first, records.staged(0));
        assertSame(second, records.staged(1));
        assertEquals(STATE_STAGED, first.state);
        assertNull(records.peekReady());
        assertEquals(0L, records.heldBytes());
        assertSame(second, records.find(0, 6));
        assertNull(records.find(1, 6));
    }

    @Test
    public void shouldHoldAcquiredRecordsInSequenceOrder()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 100);
        final KafkaShareRecord second = stage(0, 6, 2, 50);

        records.acquire(second, 1, 2000L, 2000L - 10L);
        records.acquire(first, 3, 1000L, 1000L - 10L);

        assertEquals(0, records.stagedCount());
        assertEquals(STATE_READY, first.state);
        assertEquals(3, first.deliveryCount);
        assertEquals(1000L, first.expiresAt);
        assertEquals(990L, first.deliverBy);
        assertEquals(150L, records.heldBytes());
        assertEquals(1000L, records.expiresAtMin());
        assertSame(first, records.peekReady());
    }

    @Test
    public void shouldDeliverAndRemoveRecord()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 100);
        final KafkaShareRecord second = stage(0, 6, 2, 50);
        records.acquire(first, 1, 1000L, 1000L - 10L);
        records.acquire(second, 1, 1000L, 1000L - 10L);

        records.deliver(first, 77L);

        assertEquals(STATE_DELIVERING, first.state);
        assertEquals(77L, first.ownerId);
        assertSame(second, records.peekReady());
        assertEquals(150L, records.heldBytes());

        records.delivered(first);
        assertEquals(STATE_DELIVERED, first.state);

        records.remove(first);
        assertNull(records.find(0, 5));
        assertEquals(50L, records.heldBytes());

        records.remove(second);
        assertNull(records.peekReady());
        assertEquals(0L, records.heldBytes());
        assertEquals(Long.MAX_VALUE, records.expiresAtMin());
    }

    @Test
    public void shouldRemoveStagedRecord()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 100);

        records.remove(first);

        assertEquals(0, records.stagedCount());
        assertNull(records.find(0, 5));
    }

    @Test
    public void shouldRerouteRecordsOfOwnerInSequenceOrder()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 10);
        final KafkaShareRecord second = stage(0, 6, 2, 10);
        final KafkaShareRecord third = stage(0, 7, 3, 10);
        records.acquire(first, 1, 1000L, 1000L - 10L);
        records.acquire(second, 1, 1000L, 1000L - 10L);
        records.acquire(third, 1, 1000L, 1000L - 10L);
        records.deliver(first, 11L);
        records.delivered(first);
        records.deliver(second, 22L);
        records.delivered(second);

        final int rerouted = records.reroute(11L);

        assertEquals(1, rerouted);
        assertEquals(STATE_READY, first.state);
        assertEquals(1, first.redeliveries);
        assertEquals(0L, first.ownerId);
        assertSame(first, records.peekReady());
        assertEquals(STATE_DELIVERED, second.state);
        assertEquals(30L, records.heldBytes());

        records.deliver(first, 33L);
        assertSame(third, records.peekReady());
    }

    @Test
    public void shouldCollectExpiredRecordsExceptDelivering()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 10);
        final KafkaShareRecord second = stage(0, 6, 2, 10);
        final KafkaShareRecord third = stage(0, 7, 3, 10);
        final KafkaShareRecord fourth = stage(1, 1, 4, 10);
        records.acquire(first, 1, 100L, 100L - 10L);
        records.acquire(second, 1, 100L, 100L - 10L);
        records.acquire(third, 1, 500L, 500L - 10L);
        records.deliver(first, 11L);
        records.delivered(first);
        records.deliver(second, 11L);

        final List<KafkaShareRecord> expired = new ArrayList<>();
        final int count = records.collectExpired(200L, expired);

        assertEquals(1, count);
        assertSame(first, expired.get(0));
        assertEquals(100L, records.expiresAtMin());
        assertNotNull(records.find(0, 6));
        records.remove(first);
        assertSame(fourth, records.staged(0));

        records.delivered(second);
        expired.clear();
        assertEquals(1, records.collectExpired(200L, expired));
        assertSame(second, expired.get(0));
        assertEquals(500L, records.expiresAtMin());
    }

    @Test
    public void shouldCollectAcquiredRecordsInSequenceOrder()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 10);
        final KafkaShareRecord second = stage(1, 1, 2, 10);
        final KafkaShareRecord third = stage(0, 6, 3, 10);
        stage(0, 7, 4, 10);
        records.acquire(third, 1, 1000L, 1000L - 10L);
        records.acquire(first, 1, 1000L, 1000L - 10L);
        records.acquire(second, 1, 1000L, 1000L - 10L);
        records.deliver(first, 11L);

        final List<KafkaShareRecord> acquired = new ArrayList<>();
        records.collectAcquired(acquired);

        assertEquals(3, acquired.size());
        assertSame(first, acquired.get(0));
        assertSame(second, acquired.get(1));
        assertSame(third, acquired.get(2));
    }

    @Test
    public void shouldClearRecords()
    {
        final KafkaShareRecord first = stage(0, 5, 1, 10);
        stage(0, 6, 2, 10);
        records.acquire(first, 1, 1000L, 1000L - 10L);

        records.clear();

        assertEquals(0, records.stagedCount());
        assertNull(records.peekReady());
        assertNull(records.find(0, 5));
        assertEquals(0L, records.heldBytes());
        assertEquals(Long.MAX_VALUE, records.expiresAtMin());
    }

    private KafkaShareRecord stage(
        int partitionId,
        long partitionOffset,
        long sequence,
        int bytes)
    {
        return records.stage(partitionId, partitionOffset, sequence, bytes, null, 0, 0L);
    }
}
