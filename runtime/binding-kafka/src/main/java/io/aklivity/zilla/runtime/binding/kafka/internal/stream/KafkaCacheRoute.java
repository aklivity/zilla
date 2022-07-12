/*
 * Copyright 2021-2022 Aklivity Inc.
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
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheClientProduceFactory.KafkaCacheClientProduceFan;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheMetaFactory.KafkaCacheMetaFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerDescribeFactory.KafkaCacheServerDescribeFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerFetchFactory.KafkaCacheServerFetchFanout;
import io.aklivity.zilla.runtime.binding.kafka.internal.stream.KafkaCacheServerProduceFactory.KafkaCacheServerProduceFan;

public final class KafkaCacheRoute
{
    public final long routeId;
    public final Int2ObjectHashMap<KafkaCacheClientDescribeFanout> clientDescribeFanoutsByTopic;
    public final Int2ObjectHashMap<KafkaCacheServerDescribeFanout> serverDescribeFanoutsByTopic;
    public final Int2ObjectHashMap<KafkaCacheMetaFanout> metaFanoutsByTopic;
    public final Long2ObjectHashMap<KafkaCacheClientFetchFanout> clientFetchFanoutsByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheServerFetchFanout> serverFetchFanoutsByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheClientProduceFan> clientProduceFansByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheServerProduceFan> serverProduceFansByTopicPartition;
    public final Long2ObjectHashMap<KafkaCacheClientBudget> clientBudgetsByTopic;
    public final Int2ObjectHashMap<Int2IntHashMap> leadersByPartitionId;


    public KafkaCacheRoute(
        long routeId)
    {
        this.routeId = routeId;
        this.clientDescribeFanoutsByTopic = new Int2ObjectHashMap<>();
        this.serverDescribeFanoutsByTopic = new Int2ObjectHashMap<>();
        this.metaFanoutsByTopic = new Int2ObjectHashMap<>();
        this.clientFetchFanoutsByTopicPartition = new Long2ObjectHashMap<>();
        this.serverFetchFanoutsByTopicPartition = new Long2ObjectHashMap<>();
        this.clientProduceFansByTopicPartition = new Long2ObjectHashMap<>();
        this.serverProduceFansByTopicPartition = new Long2ObjectHashMap<>();
        this.clientBudgetsByTopic = new Long2ObjectHashMap<>();
        this.leadersByPartitionId = new Int2ObjectHashMap<>();
    }

    public Int2IntHashMap supplyLeadersByPartitionId(
        String topic)
    {
        return leadersByPartitionId.computeIfAbsent(topicKey(topic), k -> new Int2IntHashMap(Integer.MIN_VALUE));
    }


    public int topicKey(
        String topic)
    {
        return System.identityHashCode(topic.intern());
    }

    public long topicPartitionKey(
        String topic,
        int partitionId)
    {
        return (((long) topicKey(topic)) << Integer.SIZE) | (partitionId & 0xFFFF_FFFFL);
    }
}
