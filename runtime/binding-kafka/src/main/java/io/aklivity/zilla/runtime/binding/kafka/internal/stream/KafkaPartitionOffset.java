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

public final class KafkaPartitionOffset
{
    private static final long[] NO_SUPERSEDED = new long[0];

    public final String topic;
    public final int partitionId;
    public final long partitionOffset;
    public final int generationId;
    public final int leaderEpoch;
    public final String metadata;
    public final long correlationId;
    public final long[] superseded;

    public KafkaPartitionOffset(
        String topic,
        int partitionId,
        long partitionOffset,
        int generationId,
        int leaderEpoch,
        String metadata)
    {
        this(topic, partitionId, partitionOffset, generationId, leaderEpoch, metadata, -1);
    }

    public KafkaPartitionOffset(
        String topic,
        int partitionId,
        long partitionOffset,
        int generationId,
        int leaderEpoch,
        String metadata,
        long correlationId)
    {
        this(topic, partitionId, partitionOffset, generationId, leaderEpoch, metadata, correlationId, NO_SUPERSEDED);
    }

    private KafkaPartitionOffset(
        String topic,
        int partitionId,
        long partitionOffset,
        int generationId,
        int leaderEpoch,
        String metadata,
        long correlationId,
        long[] superseded)
    {
        this.topic = topic;
        this.partitionId = partitionId;
        this.partitionOffset = partitionOffset;
        this.generationId = generationId;
        this.leaderEpoch = leaderEpoch;
        this.metadata = metadata;
        this.correlationId = correlationId;
        this.superseded = superseded;
    }

    public KafkaPartitionOffset supersede(
        KafkaPartitionOffset earlier)
    {
        final int count = earlier.superseded.length;
        final long[] merged = new long[count + 1 + superseded.length];
        System.arraycopy(earlier.superseded, 0, merged, 0, count);
        merged[count] = earlier.correlationId;
        System.arraycopy(superseded, 0, merged, count + 1, superseded.length);

        return new KafkaPartitionOffset(topic, partitionId, partitionOffset, generationId, leaderEpoch, metadata,
            correlationId, merged);
    }
}
