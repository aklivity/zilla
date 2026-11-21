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

import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaPacketIds.MAXIMUM_PACKET_ID;
import static io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaPacketIds.NO_PACKET_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MqttKafkaPacketIdsTest
{
    @Test
    public void shouldAllocateSequentiallyFromOne()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        assertEquals(1, packetIds.allocate(1L, 0, 10L));
        assertEquals(2, packetIds.allocate(1L, 0, 11L));
        assertEquals(3, packetIds.allocate(1L, 0, 12L));
    }

    @Test
    public void shouldResolveAllocatedPacketId()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        final int first = packetIds.allocate(11L, 1, 100L);
        final int second = packetIds.allocate(22L, 2, 200L);

        assertEquals(11L, packetIds.topicKey(first));
        assertEquals(1, packetIds.partitionId(first));
        assertEquals(100L, packetIds.offset(first));
        assertEquals(22L, packetIds.topicKey(second));
        assertEquals(2, packetIds.partitionId(second));
        assertEquals(200L, packetIds.offset(second));
        assertEquals(100L, packetIds.offset(first));
    }

    @Test
    public void shouldReleaseAllocatedPacketId()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        final int packetId = packetIds.allocate(1L, 0, 10L);

        assertTrue(packetIds.contains(packetId));
        assertTrue(packetIds.release(packetId));
        assertFalse(packetIds.contains(packetId));
        assertFalse(packetIds.release(packetId));
    }

    @Test
    public void shouldWrapFromMaximumToOneNeverZero()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        for (int i = 1; i <= MAXIMUM_PACKET_ID; i++)
        {
            assertEquals(i, packetIds.allocate(0L, 0, i));
            packetIds.release(i);
        }

        assertEquals(1, packetIds.allocate(0L, 0, 0L));
        assertEquals(2, packetIds.allocate(0L, 0, 0L));
    }

    @Test
    public void shouldNeverAllocateZero()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        for (int i = 0; i < 3 * MAXIMUM_PACKET_ID; i++)
        {
            final int packetId = packetIds.allocate(0L, 0, i);
            assertNotEquals(NO_PACKET_ID, packetId);
            assertEquals(i, packetIds.offset(packetId));
            assertTrue(packetIds.release(packetId));
        }
    }

    @Test
    public void shouldSkipPacketIdsStillInflight()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        final int first = packetIds.allocate(0L, 0, 1L);
        final int second = packetIds.allocate(0L, 0, 2L);
        final int third = packetIds.allocate(0L, 0, 3L);
        packetIds.release(second);

        for (int i = third + 1; i < MAXIMUM_PACKET_ID; i++)
        {
            packetIds.allocate(0L, 0, -1L);
        }

        assertEquals(MAXIMUM_PACKET_ID, packetIds.allocate(0L, 0, 4L));
        assertEquals(second, packetIds.allocate(0L, 0, 5L));
        assertEquals(1L, packetIds.offset(first));
        assertEquals(3L, packetIds.offset(third));
        assertEquals(5L, packetIds.offset(second));
    }

    @Test
    public void shouldSkipReservedPacketIds()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        packetIds.reserve(1, 7L, 3, 31L);
        packetIds.reserve(2, 8L, 4, 41L);

        assertEquals(3, packetIds.allocate(0L, 0, 0L));
        assertEquals(7L, packetIds.topicKey(1));
        assertEquals(3, packetIds.partitionId(1));
        assertEquals(31L, packetIds.offset(1));
        assertEquals(8L, packetIds.topicKey(2));
        assertEquals(4, packetIds.partitionId(2));
        assertEquals(41L, packetIds.offset(2));
    }

    @Test
    public void shouldReplaceReservedPacketId()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        packetIds.reserve(5, 1L, 1, 10L);
        packetIds.reserve(5, 2L, 2, 20L);

        assertEquals(2L, packetIds.topicKey(5));
        assertEquals(2, packetIds.partitionId(5));
        assertEquals(20L, packetIds.offset(5));
        assertTrue(packetIds.release(5));
        assertFalse(packetIds.contains(5));
    }

    @Test
    public void shouldReuseReleasedPacketIdAfterWrap()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        final int first = packetIds.allocate(0L, 0, 1L);
        for (int i = 2; i <= MAXIMUM_PACKET_ID; i++)
        {
            packetIds.allocate(0L, 0, -1L);
        }
        packetIds.release(first);

        assertEquals(first, packetIds.allocate(0L, 0, 2L));
    }

    @Test
    public void shouldKeepValuesWhileGrowing()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        for (int i = 1; i <= 1000; i++)
        {
            assertEquals(i, packetIds.allocate(i * 3L, i % 7, i * 5L));
        }

        for (int i = 1; i <= 1000; i++)
        {
            assertEquals(i * 3L, packetIds.topicKey(i));
            assertEquals(i % 7, packetIds.partitionId(i));
            assertEquals(i * 5L, packetIds.offset(i));
        }
    }

    @Test
    public void shouldNotMixValuesWhenReleasedSlotsAreReused()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        final int first = packetIds.allocate(1L, 1, 10L);
        final int second = packetIds.allocate(2L, 2, 20L);
        final int third = packetIds.allocate(3L, 3, 30L);
        packetIds.release(first);
        packetIds.release(third);

        final int fourth = packetIds.allocate(4L, 4, 40L);
        final int fifth = packetIds.allocate(5L, 5, 50L);

        assertEquals(2L, packetIds.topicKey(second));
        assertEquals(20L, packetIds.offset(second));
        assertEquals(4L, packetIds.topicKey(fourth));
        assertEquals(4, packetIds.partitionId(fourth));
        assertEquals(40L, packetIds.offset(fourth));
        assertEquals(5L, packetIds.topicKey(fifth));
        assertEquals(5, packetIds.partitionId(fifth));
        assertEquals(50L, packetIds.offset(fifth));
    }

    @Test
    public void shouldExhaustAtMaximumInflight()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        for (int i = 1; i <= MAXIMUM_PACKET_ID; i++)
        {
            assertEquals(i, packetIds.allocate(0L, 0, i));
        }

        assertEquals(NO_PACKET_ID, packetIds.allocate(0L, 0, 0L));
        assertEquals(NO_PACKET_ID, packetIds.allocate(0L, 0, 0L));
    }

    @Test
    public void shouldAllocateAgainAfterExhaustionWhenReleased()
    {
        final MqttKafkaPacketIds packetIds = new MqttKafkaPacketIds();

        for (int i = 1; i <= MAXIMUM_PACKET_ID; i++)
        {
            packetIds.allocate(0L, 0, i);
        }
        packetIds.release(12345);

        assertEquals(12345, packetIds.allocate(0L, 0, 99L));
        assertEquals(99L, packetIds.offset(12345));
        assertEquals(NO_PACKET_ID, packetIds.allocate(0L, 0, 0L));
    }
}
