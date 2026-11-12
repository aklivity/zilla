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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.agrona.collections.MutableInteger;
import org.agrona.collections.Object2IntHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheKeyEnvelope;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheModel;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTrailerEnvelope;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTimestampType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;
import io.aklivity.zilla.runtime.engine.EngineContext;

final class KafkaShareStaging
{
    private static final String DIRECTORY_DIGEST = "SHA-256";
    private static final long NO_AUTHORIZATION = 0L;
    private static final long NO_PRODUCER_ID = -1L;
    private static final int NO_PADDING = 0;
    private static final int ENTRY_OVERHEAD_MAX = 512;

    private final KafkaCacheEntryFW entryRO = new KafkaCacheEntryFW();

    private final EngineContext context;
    private final KafkaCachePartition partition;
    private final long bindingId;
    private final int headersSizeMax;
    private final int segmentBytes;
    private final KafkaCacheKeyEnvelope keyEnvelope;
    private final KafkaCacheTrailerEnvelope valueEnvelope;
    private final MutableInteger entryMark;
    private final MutableInteger valueMark;
    private final MutableInteger valueLimit;
    private final MutableInteger headersMark;
    private final Object2IntHashMap<KafkaCachePartition.Node> liveByNode;

    static String directory(
        int workerIndex,
        String groupId,
        String topic)
    {
        final byte[] groupIdBytes = groupId.getBytes(UTF_8);
        final byte[] topicBytes = topic.getBytes(UTF_8);

        final ByteBuffer input = ByteBuffer.allocate(Integer.BYTES + groupIdBytes.length + topicBytes.length)
            .putInt(groupIdBytes.length)
            .put(groupIdBytes)
            .put(topicBytes);

        try
        {
            final MessageDigest digest = MessageDigest.getInstance(DIRECTORY_DIGEST);
            return String.format("share-%d-%s", workerIndex, HexFormat.of().formatHex(digest.digest(input.array())));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    KafkaShareStaging(
        EngineContext context,
        KafkaCachePartition partition,
        long bindingId,
        int headersSizeMax)
    {
        this.context = context;
        this.partition = partition;
        this.bindingId = bindingId;
        this.headersSizeMax = headersSizeMax;
        this.segmentBytes = partition.segmentBytes();
        this.keyEnvelope = new KafkaCacheKeyEnvelope();
        this.valueEnvelope = new KafkaCacheTrailerEnvelope();
        this.entryMark = new MutableInteger();
        this.valueMark = new MutableInteger();
        this.valueLimit = new MutableInteger();
        this.headersMark = new MutableInteger();
        this.liveByNode = new Object2IntHashMap<>(0);
    }

    boolean fits(
        KafkaKeyFW key,
        int valueLength)
    {
        return (long) key.sizeof() + Math.max(valueLength, 0) + headersSizeMax + ENTRY_OVERHEAD_MAX <= segmentBytes;
    }

    void start(
        long traceId,
        long sequence,
        long timestamp,
        KafkaKeyFW key,
        int valueLength,
        OctetsFW valueFragment)
    {
        final KafkaCachePartition.Node previous = partition.head();
        final KafkaCachePartition.Node head =
            partition.newHeadIfNecessary(sequence, key, valueLength, NO_PADDING, headersSizeMax);

        if (head != previous && !previous.sentinel() && liveByNode.getValue(previous) == 0)
        {
            liveByNode.removeKey(previous);
            previous.remove();
        }

        partition.writeEntryStart(context, traceId, bindingId, NO_AUTHORIZATION, sequence, entryMark, valueMark,
            valueLimit, headersMark, timestamp, KafkaTimestampType.ADVISORY, NO_PRODUCER_ID, key, valueLength, NO_PADDING,
            NO_PADDING, NO_PADDING, null, 0, KafkaDeltaType.NONE, valueFragment, KafkaCacheModel.NONE,
            KafkaCacheModel.NONE, keyEnvelope, valueEnvelope, false);
    }

    void append(
        long traceId,
        int flags,
        OctetsFW valueFragment)
    {
        partition.writeEntryContinue(traceId, bindingId, NO_AUTHORIZATION, flags, entryMark, valueMark, valueLimit,
            valueFragment, KafkaCacheModel.NONE, NO_PADDING);
    }

    KafkaShareRecord finish(
        KafkaShareRecords records,
        int partitionId,
        long partitionOffset,
        long sequence,
        long timestamp,
        ArrayFW<KafkaHeaderFW> headers)
    {
        partition.writeEntryFinish(headers, KafkaDeltaType.NONE, entryMark, valueMark, headersMark, NO_PADDING,
            KafkaCacheModel.NONE, valueEnvelope, NO_PADDING);

        final KafkaCachePartition.Node node = partition.head();
        final int position = entryMark.value;
        final int bytes = node.segment().logFile().capacity() - position;

        liveByNode.put(node, liveByNode.getValue(node) + 1);

        return records.stage(partitionId, partitionOffset, sequence, bytes, node, position, timestamp);
    }

    KafkaCacheEntryFW entry(
        KafkaShareRecord record)
    {
        return record.node.segment().logFile().readBytes(record.position, entryRO::wrap);
    }

    void release(
        KafkaShareRecord record)
    {
        final KafkaCachePartition.Node node = record.node;
        final int live = liveByNode.getValue(node) - 1;

        if (live > 0)
        {
            liveByNode.put(node, live);
        }
        else
        {
            liveByNode.removeKey(node);

            if (node != partition.head())
            {
                node.remove();
            }
        }
    }

    void close()
    {
        liveByNode.clear();
        partition.deleteAll();
    }
}
