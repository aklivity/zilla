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

import java.util.Arrays;

import org.agrona.collections.Int2IntHashMap;

final class MqttKafkaPacketIds
{
    static final int NO_PACKET_ID = 0;
    static final int MAXIMUM_PACKET_ID = 0xFFFF;

    private static final int NO_SLOT = -1;
    private static final int INITIAL_CAPACITY = 16;

    private final Int2IntHashMap slots;

    private long[] topicKeys;
    private int[] partitionIds;
    private long[] offsets;
    private int[] freeSlots;
    private int freeCount;
    private int slotCount;
    private int nextPacketId;

    MqttKafkaPacketIds()
    {
        this.slots = new Int2IntHashMap(NO_SLOT);
        this.topicKeys = new long[INITIAL_CAPACITY];
        this.partitionIds = new int[INITIAL_CAPACITY];
        this.offsets = new long[INITIAL_CAPACITY];
        this.freeSlots = new int[INITIAL_CAPACITY];
        this.nextPacketId = 1;
    }

    int allocate(
        long topicKey,
        int partitionId,
        long offset)
    {
        int packetId = NO_PACKET_ID;

        if (slots.size() < MAXIMUM_PACKET_ID)
        {
            while (slots.containsKey(nextPacketId))
            {
                nextPacketId = advance(nextPacketId);
            }

            packetId = nextPacketId;
            nextPacketId = advance(nextPacketId);
            store(packetId, topicKey, partitionId, offset);
        }

        return packetId;
    }

    void reserve(
        int packetId,
        long topicKey,
        int partitionId,
        long offset)
    {
        store(packetId, topicKey, partitionId, offset);
    }

    boolean contains(
        int packetId)
    {
        return slots.containsKey(packetId);
    }

    long topicKey(
        int packetId)
    {
        return topicKeys[slots.get(packetId)];
    }

    int partitionId(
        int packetId)
    {
        return partitionIds[slots.get(packetId)];
    }

    long offset(
        int packetId)
    {
        return offsets[slots.get(packetId)];
    }

    boolean release(
        int packetId)
    {
        final int slot = slots.remove(packetId);
        final boolean released = slot != NO_SLOT;

        if (released)
        {
            freeSlots[freeCount++] = slot;
        }

        return released;
    }

    private void store(
        int packetId,
        long topicKey,
        int partitionId,
        long offset)
    {
        int slot = slots.get(packetId);

        if (slot == NO_SLOT)
        {
            slot = freeCount > 0 ? freeSlots[--freeCount] : newSlot();
            slots.put(packetId, slot);
        }

        topicKeys[slot] = topicKey;
        partitionIds[slot] = partitionId;
        offsets[slot] = offset;
    }

    private int newSlot()
    {
        if (slotCount == topicKeys.length)
        {
            final int capacity = slotCount << 1;
            topicKeys = Arrays.copyOf(topicKeys, capacity);
            partitionIds = Arrays.copyOf(partitionIds, capacity);
            offsets = Arrays.copyOf(offsets, capacity);
            freeSlots = Arrays.copyOf(freeSlots, capacity);
        }

        return slotCount++;
    }

    private static int advance(
        int packetId)
    {
        return packetId == MAXIMUM_PACKET_ID ? 1 : packetId + 1;
    }
}
