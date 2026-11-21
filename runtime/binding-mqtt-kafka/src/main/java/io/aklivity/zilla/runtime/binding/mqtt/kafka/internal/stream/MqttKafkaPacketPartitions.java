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

final class MqttKafkaPacketPartitions
{
    static final int NO_SLOT = -1;

    private static final int INITIAL_CAPACITY = 16;

    private final Int2IntHashMap heads;

    private String[] topics;
    private int[] partitionIds;
    private int[] nexts;
    private int[] freeSlots;
    private int freeCount;
    private int slotCount;

    MqttKafkaPacketPartitions()
    {
        this.heads = new Int2IntHashMap(NO_SLOT);
        this.topics = new String[INITIAL_CAPACITY];
        this.partitionIds = new int[INITIAL_CAPACITY];
        this.nexts = new int[INITIAL_CAPACITY];
        this.freeSlots = new int[INITIAL_CAPACITY];
    }

    void add(
        int packetId,
        String topic,
        int partitionId)
    {
        final int slot = freeCount > 0 ? freeSlots[--freeCount] : newSlot();

        topics[slot] = topic;
        partitionIds[slot] = partitionId;
        nexts[slot] = NO_SLOT;

        int tail = heads.get(packetId);

        if (tail == NO_SLOT)
        {
            heads.put(packetId, slot);
        }
        else
        {
            while (nexts[tail] != NO_SLOT)
            {
                tail = nexts[tail];
            }

            nexts[tail] = slot;
        }
    }

    boolean contains(
        int packetId)
    {
        return heads.containsKey(packetId);
    }

    int first(
        int packetId)
    {
        return heads.get(packetId);
    }

    int next(
        int slot)
    {
        return nexts[slot];
    }

    String topic(
        int slot)
    {
        return topics[slot];
    }

    int partitionId(
        int slot)
    {
        return partitionIds[slot];
    }

    void removeFirst(
        int packetId)
    {
        final int slot = heads.get(packetId);

        if (slot != NO_SLOT)
        {
            final int next = nexts[slot];

            if (next == NO_SLOT)
            {
                heads.remove(packetId);
            }
            else
            {
                heads.put(packetId, next);
            }

            topics[slot] = null;
            freeSlots[freeCount++] = slot;
        }
    }

    private int newSlot()
    {
        if (slotCount == topics.length)
        {
            final int capacity = slotCount << 1;
            topics = Arrays.copyOf(topics, capacity);
            partitionIds = Arrays.copyOf(partitionIds, capacity);
            nexts = Arrays.copyOf(nexts, capacity);
            freeSlots = Arrays.copyOf(freeSlots, capacity);
        }

        return slotCount++;
    }
}
