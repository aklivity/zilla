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

import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.budget.KafkaCacheClientBudget;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheClientDescribeFactory.KafkaCacheClientDescribeFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheClientFetchFactory.KafkaCacheClientFetchFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheClientMetaFactory.KafkaCacheClientMetaFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheClientProduceFactory.KafkaCacheClientProduceFan;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerDescribeFactory.KafkaCacheServerDescribeFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerFetchFactory.KafkaCacheServerFetchFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerMetaFactory.KafkaCacheServerMetaFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerProduceFactory.KafkaCacheServerProduceFan;

public final class KafkaCacheRoute
{
    public static final int LEADER_UNKNOWN = Integer.MIN_VALUE;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long MIX_MULTIPLIER_1 = 0xff51afd7ed558ccdL;
    private static final long MIX_MULTIPLIER_2 = 0xc4ceb9fe1a85ec53L;

    public final long resolvedId;
    public final Int2ObjectHashMap<KafkaCacheClientDescribeFanout> clientDescribeFanoutsByTopic;
    public final Int2ObjectHashMap<KafkaCacheServerDescribeFanout> serverDescribeFanoutsByTopic;
    public final Int2ObjectHashMap<KafkaCacheClientMetaFanout> clientMetaFanoutsByTopic;
    public final Int2ObjectHashMap<KafkaCacheServerMetaFanout> serverMetaFanoutsByTopic;
    public final Long2ObjectHashMap<KafkaCacheClientFetchFanout> clientFetchFanoutsByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheServerFetchFanout> serverFetchFanoutsByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheClientProduceFan> clientProduceFansByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheServerProduceFan> serverProduceFansByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheClientBudget> clientBudgetsByTopic;
    public final Int2ObjectHashMap<Int2IntHashMap> leadersByPartitionId;
    public final Int2ObjectHashMap<KafkaCacheServerMetaWatch> serverMetaWatchesByTopic;


    public KafkaCacheRoute(
        long resolvedId)
    {
        this.resolvedId = resolvedId;
        this.clientDescribeFanoutsByTopic = new Int2ObjectHashMap<>();
        this.serverDescribeFanoutsByTopic = new Int2ObjectHashMap<>();
        this.clientMetaFanoutsByTopic = new Int2ObjectHashMap<>();
        this.serverMetaFanoutsByTopic = new Int2ObjectHashMap<>();
        this.clientFetchFanoutsByTopicPartition = new Long2ObjectHashMap<>();
        this.serverFetchFanoutsByTopicPartition = new Long2ObjectHashMap<>();
        this.clientProduceFansByTopicPartition = new Long2ObjectHashMap<>();
        this.serverProduceFansByTopicPartition = new Long2ObjectHashMap<>();
        this.clientBudgetsByTopic = new Long2ObjectHashMap<>();
        this.leadersByPartitionId = new Int2ObjectHashMap<>();
        this.serverMetaWatchesByTopic = new Int2ObjectHashMap<>();
    }

    public static int resolveLeaderId(
        int leaderId,
        long affinity)
    {
        return leaderId == LEADER_UNKNOWN && affinity >= 0L && affinity <= Integer.MAX_VALUE ? (int) affinity : leaderId;
    }

    public Int2IntHashMap supplyLeadersByPartitionId(
        String topic)
    {
        return leadersByPartitionId.computeIfAbsent(topicKey(topic), k -> new Int2IntHashMap(LEADER_UNKNOWN));
    }


    public static int topicKey(
        String topic)
    {
        return System.identityHashCode(topic.intern());
    }

    public static long topicPartitionKey(
        String topic,
        int partitionId)
    {
        return (((long) topicKey(topic)) << Integer.SIZE) | (partitionId & 0xFFFF_FFFFL);
    }

    public static long partitionPlacementKey(
        String topic,
        int partitionId)
    {
        long hash = FNV_OFFSET_BASIS;
        for (int index = 0; index < topic.length(); index++)
        {
            hash = (hash ^ topic.charAt(index)) * FNV_PRIME;
        }

        hash ^= hash >>> 33;
        hash *= MIX_MULTIPLIER_1;
        hash ^= hash >>> 33;
        hash *= MIX_MULTIPLIER_2;
        hash ^= hash >>> 33;

        return (hash >>> 2) + partitionId;
    }
}
