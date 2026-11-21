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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaPacketPartitions.NO_SLOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MqttKafkaPacketPartitionsTest
{
    @Test
    public void shouldNotContainUnknownPacketId()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();

        assertFalse(partitions.contains(1));
        assertEquals(NO_SLOT, partitions.first(1));
    }

    @Test
    public void shouldAddAndResolvePartition()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();

        partitions.add(7, "messages", 3);

        assertTrue(partitions.contains(7));
        final int slot = partitions.first(7);
        assertEquals("messages", partitions.topic(slot));
        assertEquals(3, partitions.partitionId(slot));
        assertEquals(NO_SLOT, partitions.next(slot));
    }

    @Test
    public void shouldVisitPartitionsInInsertionOrder()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();

        partitions.add(7, "messages", 0);
        partitions.add(7, "retained", 1);
        partitions.add(7, "messages", 2);

        int slot = partitions.first(7);
        assertEquals("messages", partitions.topic(slot));
        assertEquals(0, partitions.partitionId(slot));
        slot = partitions.next(slot);
        assertEquals("retained", partitions.topic(slot));
        assertEquals(1, partitions.partitionId(slot));
        slot = partitions.next(slot);
        assertEquals("messages", partitions.topic(slot));
        assertEquals(2, partitions.partitionId(slot));
        assertEquals(NO_SLOT, partitions.next(slot));
    }

    @Test
    public void shouldRemoveFirstPartitionOnly()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();
        partitions.add(7, "messages", 0);
        partitions.add(7, "retained", 1);

        partitions.removeFirst(7);

        assertTrue(partitions.contains(7));
        final int slot = partitions.first(7);
        assertEquals("retained", partitions.topic(slot));
        assertEquals(1, partitions.partitionId(slot));
        assertEquals(NO_SLOT, partitions.next(slot));
    }

    @Test
    public void shouldDropPacketIdWhenLastPartitionRemoved()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();
        partitions.add(7, "messages", 0);

        partitions.removeFirst(7);

        assertFalse(partitions.contains(7));
        assertEquals(NO_SLOT, partitions.first(7));
    }

    @Test
    public void shouldIgnoreRemoveFirstForUnknownPacketId()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();
        partitions.add(7, "messages", 0);

        partitions.removeFirst(8);

        assertTrue(partitions.contains(7));
        assertFalse(partitions.contains(8));
    }

    @Test
    public void shouldKeepPacketIdsIndependent()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();
        partitions.add(1, "messages", 10);
        partitions.add(2, "messages", 20);

        partitions.removeFirst(1);

        assertFalse(partitions.contains(1));
        assertTrue(partitions.contains(2));
        assertEquals(20, partitions.partitionId(partitions.first(2)));
    }

    @Test
    public void shouldGrowBeyondInitialCapacity()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();

        for (int packetId = 1; packetId <= 100; packetId++)
        {
            partitions.add(packetId, "messages", packetId);
        }

        for (int packetId = 1; packetId <= 100; packetId++)
        {
            assertTrue(partitions.contains(packetId));
            assertEquals(packetId, partitions.partitionId(partitions.first(packetId)));
        }
    }

    @Test
    public void shouldReuseSlotsAfterRemoval()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();

        for (int round = 0; round < 1000; round++)
        {
            partitions.add(1, "messages", round);
            partitions.add(1, "retained", round);
            assertEquals(round, partitions.partitionId(partitions.first(1)));
            partitions.removeFirst(1);
            partitions.removeFirst(1);
            assertFalse(partitions.contains(1));
        }

        partitions.add(2, "messages", 42);
        assertEquals(42, partitions.partitionId(partitions.first(2)));
    }

    @Test
    public void shouldAppendAfterPartialRemoval()
    {
        final MqttKafkaPacketPartitions partitions = new MqttKafkaPacketPartitions();
        partitions.add(7, "messages", 0);
        partitions.add(7, "messages", 1);
        partitions.removeFirst(7);

        partitions.add(7, "messages", 2);

        int slot = partitions.first(7);
        assertEquals(1, partitions.partitionId(slot));
        slot = partitions.next(slot);
        assertEquals(2, partitions.partitionId(slot));
        assertEquals(NO_SLOT, partitions.next(slot));
    }
}
