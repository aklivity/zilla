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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MqttKafkaPacketIdsTest
{
    @Test
    public void shouldAllocateSequentiallyFromOne()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        assertEquals(1, packetIds.allocate("a"));
        assertEquals(2, packetIds.allocate("b"));
        assertEquals(3, packetIds.allocate("c"));
    }

    @Test
    public void shouldResolveAllocatedPacketId()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        final int first = packetIds.allocate("a");
        final int second = packetIds.allocate("b");

        assertEquals("a", packetIds.get(first));
        assertEquals("b", packetIds.get(second));
        assertEquals("a", packetIds.get(first));
    }

    @Test
    public void shouldReleaseAllocatedPacketId()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        final int packetId = packetIds.allocate("a");

        assertEquals("a", packetIds.release(packetId));
        assertNull(packetIds.get(packetId));
        assertNull(packetIds.release(packetId));
    }

    @Test
    public void shouldWrapFromMaximumToOneNeverZero()
    {
        final MqttKafkaPacketIds<Integer> packetIds = new MqttKafkaPacketIds<>();

        for (int i = 1; i <= MAXIMUM_PACKET_ID; i++)
        {
            assertEquals(i, packetIds.allocate(i));
            packetIds.release(i);
        }

        assertEquals(1, packetIds.allocate(0));
        assertEquals(2, packetIds.allocate(0));
    }

    @Test
    public void shouldNeverAllocateZero()
    {
        final MqttKafkaPacketIds<Integer> packetIds = new MqttKafkaPacketIds<>();

        for (int i = 0; i < 3 * MAXIMUM_PACKET_ID; i++)
        {
            final int packetId = packetIds.allocate(i);
            assertNotEquals(NO_PACKET_ID, packetId);
            assertEquals(Integer.valueOf(i), packetIds.release(packetId));
        }
    }

    @Test
    public void shouldSkipPacketIdsStillInflight()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        final int first = packetIds.allocate("first");
        final int second = packetIds.allocate("second");
        final int third = packetIds.allocate("third");
        packetIds.release(second);

        for (int i = third + 1; i < MAXIMUM_PACKET_ID; i++)
        {
            packetIds.allocate("filler");
        }

        assertEquals(MAXIMUM_PACKET_ID, packetIds.allocate("last"));
        assertEquals(second, packetIds.allocate("reused"));
        assertEquals("first", packetIds.get(first));
        assertEquals("third", packetIds.get(third));
    }

    @Test
    public void shouldSkipReservedPacketIds()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        packetIds.reserve(1, "restored1");
        packetIds.reserve(2, "restored2");

        assertEquals(3, packetIds.allocate("a"));
        assertEquals("restored1", packetIds.get(1));
        assertEquals("restored2", packetIds.get(2));
    }

    @Test
    public void shouldReuseReleasedPacketIdAfterWrap()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        final int first = packetIds.allocate("first");
        for (int i = 2; i <= MAXIMUM_PACKET_ID; i++)
        {
            packetIds.allocate("filler");
        }
        packetIds.release(first);

        assertEquals(first, packetIds.allocate("again"));
    }

    @Test
    public void shouldExhaustAtMaximumInflight()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        for (int i = 1; i <= MAXIMUM_PACKET_ID; i++)
        {
            assertEquals(i, packetIds.allocate("inflight"));
        }

        assertEquals(NO_PACKET_ID, packetIds.allocate("overflow"));
        assertEquals(NO_PACKET_ID, packetIds.allocate("overflow"));
    }

    @Test
    public void shouldAllocateAgainAfterExhaustionWhenReleased()
    {
        final MqttKafkaPacketIds<String> packetIds = new MqttKafkaPacketIds<>();

        for (int i = 1; i <= MAXIMUM_PACKET_ID; i++)
        {
            packetIds.allocate("inflight");
        }
        packetIds.release(12345);

        assertEquals(12345, packetIds.allocate("again"));
        assertEquals(NO_PACKET_ID, packetIds.allocate("overflow"));
    }
}
