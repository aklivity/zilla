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
package io.aklivity.zilla.runtime.cog.kafka.internal.cache;

import static java.lang.Double.parseDouble;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.cog.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.cog.kafka.internal.types.String16FW;

public final class KafkaCacheTopicConfig
{
    private static final String16FW CLEANUP_POLICY = new String16FW("cleanup.policy");
    private static final String16FW MAX_MESSAGE_BYTES = new String16FW("max.message.bytes");
    private static final String16FW SEGMENT_BYTES = new String16FW("segment.bytes");
    private static final String16FW SEGMENT_INDEX_BYTES = new String16FW("segment.index.bytes");
    private static final String16FW SEGMENT_MILLIS = new String16FW("segment.ms");
    private static final String16FW RETENTION_BYTES = new String16FW("retention.bytes");
    private static final String16FW RETENTION_MILLIS = new String16FW("retention.ms");

    private static final String16FW DELETE_RETENTION_MILLIS = new String16FW("delete.retention.ms");
    private static final String16FW MIN_COMPACTION_LAG_MILLIS = new String16FW("min.compaction.lag.ms");
    private static final String16FW MAX_COMPACTION_LAG_MILLIS = new String16FW("max.compaction.lag.ms");
    private static final String16FW MIN_CLEANABLE_DIRTY_RATIO = new String16FW("min.cleanable.dirty.ratio");

    private static final String16FW CLEANUP_POLICY_COMPACT = new String16FW("compact");
    private static final String16FW CLEANUP_POLICY_DELETE = new String16FW("delete");
    private static final String16FW CLEANUP_POLICY_COMPACT_DELETE = new String16FW("compact,delete");

    public volatile KafkaCacheCleanupPolicy cleanupPolicy;
    public volatile int maxMessageBytes;
    public volatile int segmentBytes;
    public volatile int segmentIndexBytes;
    public volatile long segmentMillis;
    public volatile long retentionBytes;
    public volatile long retentionMillis;

    public volatile long deleteRetentionMillis;
    public volatile long minCompactionLagMillis;
    public volatile long maxCompactionLagMillis;
    public volatile double minCleanableDirtyRatio;

    private static final Map<String16FW, BiConsumer<KafkaCacheTopicConfig, String16FW>> CHANGE_HANDLERS;

    static
    {
        final Map<String16FW, BiConsumer<KafkaCacheTopicConfig, String16FW>> changeHandlers = new HashMap<>();
        changeHandlers.put(CLEANUP_POLICY, KafkaCacheTopicConfig::onCleanupPolicyChanged);
        changeHandlers.put(MAX_MESSAGE_BYTES, KafkaCacheTopicConfig::onMaxMessageBytesChanged);
        changeHandlers.put(SEGMENT_BYTES, KafkaCacheTopicConfig::onSegmentBytesChanged);
        changeHandlers.put(SEGMENT_INDEX_BYTES, KafkaCacheTopicConfig::onSegmentIndexBytesChanged);
        changeHandlers.put(SEGMENT_MILLIS, KafkaCacheTopicConfig::onSegmentMillisChanged);
        changeHandlers.put(RETENTION_BYTES, KafkaCacheTopicConfig::onRetentionBytesChanged);
        changeHandlers.put(RETENTION_MILLIS, KafkaCacheTopicConfig::onRetentionMillisChanged);
        changeHandlers.put(DELETE_RETENTION_MILLIS, KafkaCacheTopicConfig::onDeleteRetentionMillisChanged);
        changeHandlers.put(MIN_COMPACTION_LAG_MILLIS, KafkaCacheTopicConfig::onMinCompactionLagMillisChanged);
        changeHandlers.put(MAX_COMPACTION_LAG_MILLIS, KafkaCacheTopicConfig::onMaxCompactionLagMillisChanged);
        changeHandlers.put(MIN_CLEANABLE_DIRTY_RATIO, KafkaCacheTopicConfig::onMinCleanableDirtyRatioChanged);
        CHANGE_HANDLERS = changeHandlers;
    }

    public KafkaCacheTopicConfig(
        KafkaConfiguration config)
    {
        this.cleanupPolicy = config.cacheCleanupPolicy();
        this.maxMessageBytes = config.cacheMaxMessageBytes();
        this.segmentBytes = config.cacheSegmentBytes();
        this.segmentIndexBytes = config.cacheSegmentIndexBytes();
        this.segmentMillis = config.cacheSegmentMillis();
        this.retentionBytes = config.cacheRetentionBytes();
        this.retentionMillis = config.cacheRetentionMillis();
        this.deleteRetentionMillis = config.cacheDeleteRetentionMillis();
        this.minCompactionLagMillis = config.cacheMinCompactionLagMillis();
        this.maxCompactionLagMillis = config.cacheMaxCompactionLagMillis();
        this.minCleanableDirtyRatio = config.cacheMinCleanableDirtyRatio();
    }

    public void onChanged(
        String16FW configName,
        String16FW configValue)
    {
        final BiConsumer<KafkaCacheTopicConfig, String16FW> handler = CHANGE_HANDLERS.get(configName);
        if (handler != null)
        {
            handler.accept(this, configValue);
        }
    }

    private static void onCleanupPolicyChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        if (CLEANUP_POLICY_COMPACT.equals(value))
        {
            topicConfig.cleanupPolicy = KafkaCacheCleanupPolicy.COMPACT;
        }
        else if (CLEANUP_POLICY_DELETE.equals(value))
        {
            topicConfig.cleanupPolicy = KafkaCacheCleanupPolicy.DELETE;
        }
        else if (CLEANUP_POLICY_COMPACT_DELETE.equals(value))
        {
            topicConfig.cleanupPolicy = KafkaCacheCleanupPolicy.COMPACT_AND_DELETE;
        }
        else
        {
            System.out.format("Unrecognized cleanup policy: %s\n", value.asString());
            topicConfig.cleanupPolicy = KafkaCacheCleanupPolicy.UNKNOWN;
        }
    }

    private static void onMaxMessageBytesChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.maxMessageBytes = parseIntAscii(value);
    }

    private static void onSegmentBytesChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.segmentBytes = parseIntAscii(value);
    }

    private static void onSegmentIndexBytesChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.segmentIndexBytes = parseIntAscii(value);
    }

    private static void onSegmentMillisChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.segmentMillis = parseLongAscii(value);
    }

    private static void onRetentionBytesChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.retentionBytes = parseLongAscii(value);
    }

    private static void onRetentionMillisChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.retentionMillis = parseLongAscii(value);
    }

    private static void onDeleteRetentionMillisChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.deleteRetentionMillis = parseLongAscii(value);
    }

    private static void onMinCompactionLagMillisChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.minCompactionLagMillis = parseLongAscii(value);
    }

    private static void onMaxCompactionLagMillisChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.maxCompactionLagMillis = parseLongAscii(value);
    }

    private static void onMinCleanableDirtyRatioChanged(
        KafkaCacheTopicConfig topicConfig,
        String16FW value)
    {
        topicConfig.minCleanableDirtyRatio = parseDoubleAscii(value);
    }

    private static int parseIntAscii(
        String16FW value)
    {
        final DirectBuffer text = value.value();
        return text.parseIntAscii(0, text.capacity());
    }

    private static long parseLongAscii(
        String16FW value)
    {
        final DirectBuffer text = value.value();
        return text.parseLongAscii(0, text.capacity());
    }

    private static double parseDoubleAscii(
        String16FW value)
    {
        final DirectBuffer text = value.value();
        return parseDouble(text.getStringWithoutLengthAscii(0, text.capacity()));
    }
}
