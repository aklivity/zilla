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

import java.util.function.LongConsumer;

import org.agrona.collections.ArrayUtil;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.binding.kafka.KafkaServerConfig;

public final class KafkaClientRoute
{
    private static final LongConsumer[] NO_META_FLUSHES = new LongConsumer[0];
    private static final LongConsumer[] NO_META_REFRESHES = new LongConsumer[0];

    public final long resolvedId;
    public final Long2ObjectHashMap<KafkaServerConfig> servers;
    public final Int2ObjectHashMap<Int2IntHashMap> partitions;
    public final LongConsumer metaFlush = this::flushMeta;
    public final LongConsumer metaRefreshed = this::refreshedMeta;

    private LongConsumer[] metaFlushes = NO_META_FLUSHES;
    private LongConsumer[] metaRefreshes = NO_META_REFRESHES;

    public KafkaClientRoute(
        long resolvedId)
    {
        this.resolvedId = resolvedId;
        this.servers = new Long2ObjectHashMap<>();
        this.partitions = new Int2ObjectHashMap<>();
    }

    public void registerMetaFlush(
        LongConsumer flush)
    {
        metaFlushes = ArrayUtil.add(metaFlushes, flush);
    }

    public void unregisterMetaFlush(
        LongConsumer flush,
        long traceId)
    {
        metaFlushes = ArrayUtil.remove(metaFlushes, flush);

        if (!hasMeta())
        {
            refreshedMeta(traceId);
        }
    }

    public boolean hasMeta()
    {
        return metaFlushes.length != 0;
    }

    public void registerMetaRefreshed(
        LongConsumer refreshed)
    {
        metaRefreshes = ArrayUtil.add(metaRefreshes, refreshed);
    }

    public void unregisterMetaRefreshed(
        LongConsumer refreshed)
    {
        metaRefreshes = ArrayUtil.remove(metaRefreshes, refreshed);
    }

    private void flushMeta(
        long traceId)
    {
        for (LongConsumer flush : metaFlushes)
        {
            flush.accept(traceId);
        }
    }

    private void refreshedMeta(
        long traceId)
    {
        for (LongConsumer refreshed : metaRefreshes)
        {
            refreshed.accept(traceId);
        }
    }

    public Int2IntHashMap supplyPartitions(
        String topic)
    {
        int topicKey = System.identityHashCode(topic.intern());
        return partitions.computeIfAbsent(topicKey, k -> new Int2IntHashMap(-1));
    }
}
