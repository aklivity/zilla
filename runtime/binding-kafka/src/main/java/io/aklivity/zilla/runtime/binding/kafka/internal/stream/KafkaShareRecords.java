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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;

final class KafkaShareRecords
{
    private static final Comparator<KafkaShareRecord> BY_SEQUENCE = Comparator.comparingLong(r -> r.sequence);

    private final Int2ObjectHashMap<Long2ObjectHashMap<KafkaShareRecord>> recordsByPartition;
    private final List<KafkaShareRecord> staged;
    private final Queue<KafkaShareRecord> ready;
    private final List<KafkaShareRecord> free;

    private long heldBytes;
    private long expiresAtMin;

    KafkaShareRecords()
    {
        this.recordsByPartition = new Int2ObjectHashMap<>();
        this.staged = new ArrayList<>();
        this.ready = new PriorityQueue<>(BY_SEQUENCE);
        this.free = new ArrayList<>();
        this.expiresAtMin = Long.MAX_VALUE;
    }

    KafkaShareRecord stage(
        int partitionId,
        long partitionOffset,
        long sequence,
        int bytes,
        KafkaCachePartition.Node node,
        int position,
        long timestamp)
    {
        final KafkaShareRecord record = free.isEmpty() ? new KafkaShareRecord() : free.remove(free.size() - 1);

        record.partitionId = partitionId;
        record.partitionOffset = partitionOffset;
        record.sequence = sequence;
        record.bytes = bytes;
        record.node = node;
        record.position = position;
        record.timestamp = timestamp;
        record.deliveryCount = 0;
        record.redeliveries = 0;
        record.expiresAt = Long.MAX_VALUE;
        record.deliverBy = Long.MAX_VALUE;
        record.ownerId = 0L;
        record.state = STATE_STAGED;

        recordsByPartition.computeIfAbsent(partitionId, id -> new Long2ObjectHashMap<>()).put(partitionOffset, record);
        staged.add(record);

        return record;
    }

    int stagedCount()
    {
        return staged.size();
    }

    KafkaShareRecord staged(
        int index)
    {
        return staged.get(index);
    }

    void acquire(
        KafkaShareRecord record,
        int deliveryCount,
        long expiresAt,
        long deliverBy)
    {
        assert record.state == STATE_STAGED;

        staged.remove(record);

        record.deliveryCount = deliveryCount;
        record.expiresAt = expiresAt;
        record.deliverBy = deliverBy;
        record.state = STATE_READY;

        ready.add(record);
        heldBytes += record.bytes;
        expiresAtMin = Math.min(expiresAtMin, expiresAt);
    }

    KafkaShareRecord peekReady()
    {
        return ready.peek();
    }

    void deliver(
        KafkaShareRecord record,
        long ownerId)
    {
        assert record.state == STATE_READY;

        ready.remove(record);

        record.ownerId = ownerId;
        record.state = STATE_DELIVERING;
    }

    void delivered(
        KafkaShareRecord record)
    {
        assert record.state == STATE_DELIVERING;

        record.state = STATE_DELIVERED;
    }

    KafkaShareRecord find(
        int partitionId,
        long partitionOffset)
    {
        final Long2ObjectHashMap<KafkaShareRecord> partition = recordsByPartition.get(partitionId);

        return partition != null ? partition.get(partitionOffset) : null;
    }

    void remove(
        KafkaShareRecord record)
    {
        if (record.state == STATE_STAGED)
        {
            staged.remove(record);
        }
        else
        {
            if (record.state == STATE_READY)
            {
                ready.remove(record);
            }

            heldBytes -= record.bytes;
        }

        final Long2ObjectHashMap<KafkaShareRecord> partition = recordsByPartition.get(record.partitionId);
        partition.remove(record.partitionOffset);

        if (partition.isEmpty())
        {
            recordsByPartition.remove(record.partitionId);
        }

        record.node = null;
        free.add(record);

        if (heldBytes == 0L)
        {
            expiresAtMin = Long.MAX_VALUE;
        }
    }

    int reroute(
        long ownerId)
    {
        int rerouted = 0;

        for (Long2ObjectHashMap<KafkaShareRecord> partition : recordsByPartition.values())
        {
            for (KafkaShareRecord record : partition.values())
            {
                if (record.ownerId == ownerId && (record.state == STATE_DELIVERING || record.state == STATE_DELIVERED))
                {
                    record.ownerId = 0L;
                    record.redeliveries++;
                    record.state = STATE_READY;
                    ready.add(record);
                    rerouted++;
                }
            }
        }

        return rerouted;
    }

    long expiresAtMin()
    {
        return expiresAtMin;
    }

    int collectExpired(
        long now,
        List<KafkaShareRecord> expired)
    {
        int count = 0;
        long min = Long.MAX_VALUE;

        for (Long2ObjectHashMap<KafkaShareRecord> partition : recordsByPartition.values())
        {
            for (KafkaShareRecord record : partition.values())
            {
                if (record.state != STATE_STAGED)
                {
                    if (record.state != STATE_DELIVERING && record.expiresAt <= now)
                    {
                        expired.add(record);
                        count++;
                    }
                    else
                    {
                        min = Math.min(min, record.expiresAt);
                    }
                }
            }
        }

        expiresAtMin = min;

        return count;
    }

    void collectAcquired(
        List<KafkaShareRecord> acquired)
    {
        final int start = acquired.size();

        for (Long2ObjectHashMap<KafkaShareRecord> partition : recordsByPartition.values())
        {
            for (KafkaShareRecord record : partition.values())
            {
                if (record.state != STATE_STAGED)
                {
                    acquired.add(record);
                }
            }
        }

        acquired.subList(start, acquired.size()).sort(BY_SEQUENCE);
    }

    long heldBytes()
    {
        return heldBytes;
    }

    void clear()
    {
        recordsByPartition.clear();
        staged.clear();
        ready.clear();
        heldBytes = 0L;
        expiresAtMin = Long.MAX_VALUE;
    }
}
