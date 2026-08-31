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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.NEXT_SEGMENT_VALUE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.RETRY_SEGMENT_VALUE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorValue;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheIndexRecord.SIZEOF_INDEX_RECORD;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType.JSON_PATCH;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_ACKNOWLEDGE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_ACK_MODE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_ANCESTOR;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_CONVERTED_POSITION;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_DELTA_POSITION;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_DESCENDANT;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_FLAGS;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_OFFSET;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_OWNER_ID;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_PADDED_KEY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_PRODUCER_EPOCH;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_PRODUCER_ID;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_SEQUENCE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_TIMESTAMP;
import static java.nio.ByteBuffer.allocateDirect;
import static java.util.Objects.requireNonNull;
import static org.agrona.BitUtil.SIZE_OF_INT;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.zip.CRC32C;

import jakarta.json.JsonArray;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.spi.JsonProvider;

import org.agrona.LangUtil;
import org.agrona.collections.MutableInteger;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.ExpandableDirectBufferOutputStream;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTimestampType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheDeltaFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFlags;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCachePaddedKeyFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableArrayBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.ExpandableDirectByteBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;

public final class KafkaCachePartition
{
    public static final int CACHE_ENTRY_FLAGS_DIRTY = KafkaCacheEntryFlags.DIRTY.value();
    public static final int CACHE_ENTRY_FLAGS_COMPLETED = KafkaCacheEntryFlags.COMPLETED.value();
    public static final int CACHE_ENTRY_FLAGS_ABORTED = KafkaCacheEntryFlags.ABORTED.value();
    public static final int CACHE_ENTRY_FLAGS_CONTROL = KafkaCacheEntryFlags.CONTROL.value();
    public static final int CACHE_ENTRY_FLAGS_AUTHORITATIVE = KafkaCacheEntryFlags.AUTHORITATIVE.value();
    public static final int CACHE_ENTRY_FLAGS_ADVANCE = CACHE_ENTRY_FLAGS_COMPLETED | CACHE_ENTRY_FLAGS_DIRTY;

    private static final long NO_DIRTY_SINCE = -1L;
    private static final long NO_ANCESTOR_OFFSET = -1L;
    private static final long NO_DESCENDANT_OFFSET = -1L;
    private static final int NO_SEQUENCE = -1;
    private static final int NO_ACKNOWLEDGE = 0;
    private static final int NO_CONVERTED_POSITION = -1;
    private static final int NO_DELTA_POSITION = -1;

    private static final String FORMAT_FETCH_PARTITION_DIRECTORY = "%s-%d";
    private static final String FORMAT_PRODUCE_PARTITION_DIRECTORY = "%s-%d-%d";

    private static final int FLAGS_COMPLETE = 0x03;
    private static final int FLAGS_FIN = 0x01;

    private static final long OFFSET_HISTORICAL = KafkaOffsetType.HISTORICAL.value();

    private static final Array32FW<KafkaHeaderFW> EMPTY_TRAILERS =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(new UnsafeBufferEx(new byte[8]), 0, 8)
                .build();
    private static final int SIZEOF_EMPTY_TRAILERS = EMPTY_TRAILERS.sizeof();

    private static final int SIZEOF_PADDING_LENGTH = Integer.BYTES;

    private final KafkaCachePaddedKeyFW paddedKeyRO = new KafkaCachePaddedKeyFW();
    private final KafkaCacheEntryFW headEntryRO = new KafkaCacheEntryFW();
    private final KafkaCacheEntryFW logEntryRO = new KafkaCacheEntryFW();
    private final KafkaCacheDeltaFW deltaEntryRO = new KafkaCacheDeltaFW();

    private final MutableDirectBufferEx entryInfo = new UnsafeBufferEx(new byte[FIELD_OFFSET_PADDED_KEY]);
    private final MutableDirectBufferEx valueInfo = new UnsafeBufferEx(new byte[Integer.BYTES]);
    private final MutableInteger entryValueLimit = new MutableInteger(0);
    private final MutableInteger entryHeadersMark = new MutableInteger(0);

    private final Varint32FW varintRO = new Varint32FW();
    private final KafkaCachePaddedKeyFW.Builder paddedKeyRW = new KafkaCachePaddedKeyFW.Builder()
        .wrap(new UnsafeBufferEx(new byte[8192]), 0, 8192);
    private final Varint32FW.Builder varintRW = new Varint32FW.Builder().wrap(new UnsafeBufferEx(new byte[5]), 0, 5);
    private final Array32FW<KafkaHeaderFW> headersRO = new Array32FW<KafkaHeaderFW>(new KafkaHeaderFW());
    private final Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> trailersRW =
        new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
            .wrap(new ExpandableDirectByteBufferEx(512), 0, 8192);

    private final DirectBufferInputStream ancestorIn = new DirectBufferInputStream();
    private final DirectBufferInputStream headIn = new DirectBufferInputStream();
    private final MutableDirectBufferEx diffBuffer = new ExpandableArrayBufferEx();
    private final ExpandableDirectBufferOutputStream diffOut = new ExpandableDirectBufferOutputStream();

    private final Path location;
    private final KafkaCacheTopicConfig config;
    private final String cache;
    private final String topic;
    private final int id;
    private final MutableDirectBufferEx appendBuf;
    private final IntFunction<long[]> sortSpaceRef;
    private final Node sentinel;
    private final CRC32C checksum;

    private long progress;

    private KafkaCacheEntryFW ancestorEntry;
    private final AtomicLong produceCapacity;
    private final OctetsFW octetsRO = new OctetsFW();
    private final KafkaKeyFW keyRO = new KafkaKeyFW();

    public KafkaCachePartition(
        Path location,
        KafkaCacheTopicConfig config,
        String cache,
        String topic,
        int id,
        int appendCapacity,
        IntFunction<long[]> sortSpaceRef)
    {
        this.location = createDirectories(location.resolve(String.format(FORMAT_FETCH_PARTITION_DIRECTORY, topic, id)));
        this.config = config;
        this.cache = cache;
        this.topic = topic;
        this.id = id;
        this.appendBuf = new UnsafeBufferEx(allocateDirect(appendCapacity));
        this.sortSpaceRef = sortSpaceRef;
        this.sentinel = new Node();
        this.checksum = new CRC32C();
        this.progress = OFFSET_HISTORICAL;
        this.produceCapacity = new AtomicLong(0);
    }

    public KafkaCachePartition(
        Path location,
        KafkaCacheTopicConfig config,
        String cache,
        AtomicLong produceCapacity,
        long maxProduceCapacity,
        String topic,
        int id,
        int appendCapacity,
        IntFunction<long[]> sortSpaceRef,
        int index)
    {
        this.location = createDirectories(location.resolve(String.format(FORMAT_PRODUCE_PARTITION_DIRECTORY, topic, id, index)));
        this.config = config;
        this.cache = cache;
        this.produceCapacity = produceCapacity;
        this.topic = topic;
        this.id = id;
        this.appendBuf = new UnsafeBufferEx(allocateDirect(appendCapacity));
        this.sortSpaceRef = sortSpaceRef;
        this.sentinel = new Node();
        this.checksum = new CRC32C();
        this.progress = OFFSET_HISTORICAL;
    }

    public String cache()
    {
        return cache;
    }

    public String topic()
    {
        return topic;
    }

    public int id()
    {
        return id;
    }

    public Node sentinel()
    {
        return sentinel;
    }

    public Node head()
    {
        return sentinel.previous;
    }

    public int segmentBytes()
    {
        return config.segmentBytes;
    }

    public long nextOffset(
        KafkaOffsetType defaultOffset)
    {
        final Node head = sentinel.previous;
        return head == sentinel ? defaultOffset.value() : head.segment().nextOffset();
    }

    public Node append(
        long offset)
    {
        assert offset >= progress;

        final Node head = sentinel.previous;

        KafkaCacheSegment segment = new KafkaCacheSegment(location, config, topic, id, offset, appendBuf, sortSpaceRef);
        Node node = new Node(segment);
        node.previous = head;
        node.next = sentinel;
        node.previous.next = node;
        node.next.previous = node;

        if (!head.sentinel())
        {
            final KafkaCacheSegment tail = head.segment.freeze();
            head.segment(tail);
        }

        produceCapacity.getAndAdd(segmentBytes());

        return node;
    }

    public Node seekNotBefore(
        long offset)
    {
        Node node = sentinel.next;

        while (node != sentinel && node.segment.baseOffset() < offset)
        {
            node = node.next;
        }

        return node;
    }

    public Node seekNotAfter(
        long offset)
    {
        Node node = sentinel.previous;

        while (node != sentinel && node.segment.baseOffset() > offset)
        {
            node = node.previous;
        }

        return node;
    }

    public void newHeadIfNecessary(
        long offset)
    {
        if (head().sentinel())
        {
            append(offset);
        }
    }

    public Node newHeadIfNecessary(
        long offset,
        KafkaKeyFW key,
        int valueLength,
        int valuePaddingMax,
        int headersSizeMax)
    {
        Node head = sentinel.previous;

        if (head == sentinel)
        {
            head = append(offset);
        }
        else
        {
            final int logRequired = entryInfo.capacity() + key.sizeof() + valueInfo.capacity() +
                    Math.max(valueLength, 0) + SIZEOF_PADDING_LENGTH + valuePaddingMax + headersSizeMax;
            final int hashKeyRequired = key.length() != -1 ? 1 : 0;
            final int hashHeaderRequiredMax = headersSizeMax >> 2;
            final int hashRequiredMax = (hashKeyRequired + hashHeaderRequiredMax) * SIZEOF_INDEX_RECORD;

            KafkaCacheSegment headSegment = head.segment;
            int logRemaining = headSegment.logFile().available();
            int indexRemaining = headSegment.indexFile().available();
            int hashRemaining = headSegment.hashFile().available();
            int nullsRemaining = headSegment.nullsFile().available();
            if (logRemaining < logRequired ||
                indexRemaining < SIZEOF_INDEX_RECORD ||
                hashRemaining < hashRequiredMax ||
                nullsRemaining < SIZEOF_INDEX_RECORD)
            {
                head = append(offset);
                headSegment = head.segment;
                logRemaining = headSegment.logFile().available();
                indexRemaining = headSegment.indexFile().available();
                hashRemaining = headSegment.hashFile().available();
                nullsRemaining = headSegment.nullsFile().available();
            }
            assert logRemaining >= logRequired;
            assert indexRemaining >= SIZEOF_INDEX_RECORD;
            assert hashRemaining >= hashRequiredMax;
            assert nullsRemaining >= SIZEOF_INDEX_RECORD;
        }

        return head;
    }

    public void writeEntry(
        EngineContext context,
        long traceId,
        long bindingId,
        long authorization,
        long offset,
        MutableInteger entryMark,
        MutableInteger valueMark,
        long timestamp,
        KafkaTimestampType timestampType,
        long producerId,
        KafkaKeyFW key,
        ArrayFW<KafkaHeaderFW> headers,
        OctetsFW value,
        int entryFlags,
        KafkaDeltaType deltaType,
        KafkaCacheModel transformKey,
        KafkaCacheModel transformValue,
        KafkaCacheKeyEnvelope keyEnvelope,
        KafkaCacheTrailerEnvelope valueEnvelope,
        int trailersSizeMax,
        boolean verbose)
    {
        final int valueLength = value != null ? value.sizeof() : -1;
        final int valuePaddingMax = valueLength != -1 && transformValue != KafkaCacheModel.NONE
            ? transformValue.padding(value.buffer(), value.offset(), valueLength)
            : 0;
        final int headersMax = headers.sizeof();
        entryValueLimit.value = 0;
        writeEntryStart(context, traceId, bindingId, authorization, offset, entryMark, valueMark, entryValueLimit,
            entryHeadersMark, timestamp, timestampType, producerId, key, valueLength, valuePaddingMax, headersMax,
            trailersSizeMax, null, entryFlags, deltaType, value, transformKey, transformValue, keyEnvelope, valueEnvelope,
            verbose);
        if (value != null)
        {
            writeEntryContinue(traceId, bindingId, authorization, FLAGS_COMPLETE, entryMark, valueMark, entryValueLimit,
                value, transformValue);
        }
        writeEntryFinish(headers, deltaType, entryMark, valueMark, entryHeadersMark, headersMax, transformValue,
            valueEnvelope, trailersSizeMax);
    }

    public void writeEntryStart(
        EngineContext context,
        long traceId,
        long bindingId,
        long authorization,
        long offset,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger valueLimit,
        MutableInteger headersMark,
        long timestamp,
        KafkaTimestampType timestampType,
        long producerId,
        KafkaKeyFW key,
        int valueLength,
        int valuePaddingMax,
        int headersMax,
        int trailersSizeMax,
        IntFunction<KafkaCacheEntryFW> findAncestor,
        int entryFlags,
        KafkaDeltaType deltaType,
        OctetsFW payload,
        KafkaCacheModel transformKey,
        KafkaCacheModel transformValue,
        KafkaCacheKeyEnvelope keyEnvelope,
        KafkaCacheTrailerEnvelope valueEnvelope,
        boolean verbose)
    {
        assert offset > this.progress : String.format("%d > %d", offset, this.progress);
        this.progress = offset;

        final Node head = sentinel.previous;
        assert head != sentinel;

        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        segment.modifiedAt(timestamp, timestampType);

        final KafkaCacheFile logFile = segment.logFile();
        final KafkaCacheFile deltaFile = segment.deltaFile();
        final KafkaCacheFile hashFile = segment.hashFile();
        final KafkaCacheFile keysFile = segment.keysFile();
        final KafkaCacheFile nullsFile = segment.nullsFile();

        final int valueMaxLength = valueLength == -1 ? 0 : valueLength;

        logFile.mark();

        // a value transform's output now streams directly into this entry's own paddedValue
        // reservation below (see writeEntryContinue), so this entry never has convertedFile
        // content -- convertedPosition stays NO_CONVERTED_POSITION regardless of transformValue
        final int convertedPos = NO_CONVERTED_POSITION;

        entryMark.value = logFile.capacity();

        entryInfo.putLong(FIELD_OFFSET_OFFSET, progress);
        entryInfo.putLong(FIELD_OFFSET_TIMESTAMP, timestamp);
        entryInfo.putLong(FIELD_OFFSET_OWNER_ID, producerId);
        entryInfo.putLong(FIELD_OFFSET_ACKNOWLEDGE, NO_ACKNOWLEDGE);
        entryInfo.putInt(FIELD_OFFSET_SEQUENCE, NO_SEQUENCE);
        entryInfo.putLong(FIELD_OFFSET_ANCESTOR, NO_ANCESTOR_OFFSET);
        entryInfo.putLong(FIELD_OFFSET_DESCENDANT, NO_DESCENDANT_OFFSET);
        entryInfo.putInt(FIELD_OFFSET_FLAGS, entryFlags);
        entryInfo.putInt(FIELD_OFFSET_CONVERTED_POSITION, convertedPos);
        entryInfo.putInt(FIELD_OFFSET_DELTA_POSITION, NO_DELTA_POSITION);
        entryInfo.putShort(FIELD_OFFSET_ACK_MODE, KafkaAckMode.NONE.value());

        logFile.appendBytes(entryInfo);
        final int keyAt = logFile.capacity();

        if (key.value() == null)
        {
            logFile.appendBytes(key);
            logFile.appendInt(0);
        }
        else
        {
            Varint32FW initLength = varintRW.set(0).build();
            logFile.appendBytes(initLength);

            final KafkaCacheModel.Output writeKey = (buffer, index, length) ->
            {
                Varint32FW progress = logFile.readBytes(keyAt, varintRO::wrap);
                Varint32FW newLength = varintRW.set(progress.value() + length).build();
                int keyShift = newLength.sizeof() - progress.sizeof();
                if (keyShift > 0)
                {
                    OctetsFW octets = logFile.readBytes(progress.limit(), progress.limit() + progress.value(),  octetsRO::wrap);
                    logFile.writeBytes(newLength.limit(), octets);

                    logFile.advance(keyAt + newLength.limit());
                }
                logFile.writeBytes(keyAt, newLength);
                logFile.appendBytes(buffer, index, length);
            };

            keyEnvelope.reset();

            OctetsFW value = key.value();
            int transformed = transformKey.transform(traceId, bindingId, authorization,
                    value.buffer(), value.offset(), value.limit(), writeKey);

            if (transformed == -1)
            {
                logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                if (verbose)
                {
                    System.out.printf("%s:%s %s: Skipping invalid message on topic %s, partition %d, offset %d\n",
                        System.currentTimeMillis(), context.supplyNamespace(bindingId),
                        context.supplyLocalName(bindingId), topic, id, offset);
                }
            }
            logFile.appendInt(0);

            if (transformed != -1 && !keyEnvelope.isEmpty())
            {
                final boolean committed =
                    commitKeyOverride(logFile, entryMark.value, keyEnvelope.get(KafkaCacheKeyEnvelope.NAME, 0));
                if (!committed)
                {
                    logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                }
            }
        }

        logFile.appendInt(valueLength);

        valueMark.value = logFile.capacity();
        valueLimit.value = valueMark.value;

        final int paddingLenAt = valueMark.value + valueMaxLength;
        logFile.advance(paddingLenAt + SIZEOF_PADDING_LENGTH + valuePaddingMax);
        logFile.writeInt(paddingLenAt, valuePaddingMax);

        headersMark.value = logFile.capacity();
        if (valueLength != -1 && transformValue != KafkaCacheModel.NONE)
        {
            // headers aren't known until writeEntryFinish, but a composed transform's envelope must
            // already be claimed before the first writeEntryContinue call -- so this reserves headersMax
            // (a worst-case bound) immediately followed by trailersSizeMax, and claims the trailersSizeMax
            // portion as scratch for envelope.set() calls during the value drive. writeEntryFinish later
            // writes the real (smaller) headers and trailers contiguously starting at headersMark, reusing
            // these same bytes once the scratch content has been drained, and folds whatever is left over
            // into the entry's own trailing paddingLen/padding fields
            final int reservedMax = headersMax + trailersSizeMax + SIZEOF_PADDING_LENGTH;
            logFile.advance(headersMark.value + reservedMax);

            valueEnvelope.reset();
            valueEnvelope.claim(logFile, headersMark.value + headersMax, trailersSizeMax);
        }

        final long keyHash = computeHash(logFile.readBytes(keyAt, keyRO::wrap));

        final KafkaCacheEntryFW ancestor = findAncestor != null ? findAncestor.apply((int) keyHash) : null;

        final long ancestorOffset = ancestor != null ? ancestor.offset$() : NO_ANCESTOR_OFFSET;
        final int deltaPosition = deltaType == JSON_PATCH &&
                                  ancestor != null && ancestor.paddedValue().length() != -1 &&
                                  valueLength != -1
                    ? deltaFile.capacity()
                    : NO_DELTA_POSITION;

        logFile.writeLong(entryMark.value + FIELD_OFFSET_ANCESTOR, ancestorOffset);
        logFile.writeInt(entryMark.value + FIELD_OFFSET_DELTA_POSITION, deltaPosition);

        assert deltaPosition == NO_DELTA_POSITION || ancestor != null;
        this.ancestorEntry = ancestor;

        final long hashEntry = keyHash << 32 | logFile.markValue();
        hashFile.appendLong(hashEntry);

        if (valueLength == -1)
        {
            final int timestampDelta = (int)((timestamp - segment.timestamp()) & 0xFFFF_FFFFL);
            final long nullsEntry = timestampDelta << 32 | logFile.markValue();
            nullsFile.appendLong(nullsEntry);
        }

        final int deltaBaseOffset = 0;
        final long keyEntry = keyHash << 32 | deltaBaseOffset;
        keysFile.appendLong(keyEntry);
    }

    public int writeEntryContinue(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger valueLimit,
        OctetsFW payload,
        KafkaCacheModel transformValue)
    {
        final Node head = sentinel.previous;
        assert head != sentinel;

        final KafkaCacheSegment headSegment = head.segment;
        assert headSegment != null;

        final KafkaCacheFile logFile = headSegment.logFile();

        int transformed = 0;
        if (payload != null)
        {
            if (transformValue == KafkaCacheModel.NONE)
            {
                valueLimit.value += logFile.writeBytes(valueLimit.value, payload);
            }
            else
            {
                // re-derives the reservation's total capacity from the placeholder length/paddingLen
                // fields writeEntryStart already wrote -- both still hold their original, provisional
                // values until the COMPLETE branch below finalizes them, so this is stable across every
                // fragment of the same value
                final int valueMaxLength = Math.max(logFile.readInt(valueMark.value - SIZE_OF_INT), 0);
                final int paddingLenAt = valueMark.value + valueMaxLength;
                final int valuePaddingMax = logFile.readInt(paddingLenAt);
                final int reservedMax = valueMaxLength + valuePaddingMax;

                final KafkaCacheModel.Output consumeTransformed = (buffer, index, length) ->
                {
                    final int written = valueLimit.value - valueMark.value;
                    if (written + length > reservedMax)
                    {
                        logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                    }
                    else
                    {
                        logFile.writeBytes(valueLimit.value, buffer, index, length);
                        valueLimit.value += length;
                    }
                };

                final KafkaCacheModel.Result result = transformValue.transform(traceId, bindingId, authorization, flags,
                    payload.buffer(), payload.offset(), payload.limit(), consumeTransformed);

                if (result.status() == ModelStatus.REJECTED)
                {
                    transformed = -1;
                    logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                }
                else if (result.status() == ModelStatus.COMPLETE)
                {
                    // the transformed value's real length rarely matches the reservation sized from the
                    // raw value -- finalize length in place and relocate paddingLen to absorb whatever
                    // slack remains, exactly as commitKeyOverride does for a transformed key
                    final int actualLength = valueLimit.value - valueMark.value;
                    final int finalPaddingLen = reservedMax - actualLength;
                    logFile.writeInt(valueMark.value - SIZE_OF_INT, actualLength);
                    logFile.writeInt(valueLimit.value, finalPaddingLen);
                }
            }
        }

        return transformed;
    }

    public void writeEntryFinish(
        ArrayFW<KafkaHeaderFW> headers,
        KafkaDeltaType deltaType,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger headersMark,
        int headersMax,
        KafkaCacheModel transformValue,
        KafkaCacheTrailerEnvelope valueEnvelope,
        int trailersSizeMax)
    {
        final Node head = sentinel.previous;
        assert head != sentinel;

        final KafkaCacheSegment headSegment = head.segment;
        assert headSegment != null;

        final KafkaCacheFile logFile = headSegment.logFile();
        final KafkaCacheFile deltaFile = headSegment.deltaFile();
        final KafkaCacheFile hashFile = headSegment.hashFile();
        final KafkaCacheFile indexFile = headSegment.indexFile();

        final int valueLength = logFile.readInt(valueMark.value - SIZE_OF_INT);
        final int paddingLenAt = valueMark.value + Math.max(valueLength, 0);
        final int valuePaddingMax = logFile.readInt(paddingLenAt);
        final int valueEnd = paddingLenAt + SIZEOF_PADDING_LENGTH + valuePaddingMax;
        assert headersMark.value == valueEnd;

        Array32FW<KafkaHeaderFW> trailers = EMPTY_TRAILERS;

        if (valueLength != -1 && transformValue != KafkaCacheModel.NONE)
        {
            assert logFile.capacity() == headersMark.value + headersMax + trailersSizeMax + SIZEOF_PADDING_LENGTH;

            final boolean aborted =
                (logFile.readInt(entryMark.value + FIELD_OFFSET_FLAGS) & CACHE_ENTRY_FLAGS_ABORTED) != 0x00;

            if (!aborted)
            {
                if (!valueEnvelope.isEmpty())
                {
                    valueEnvelope.writeHeaders(trailersRW.wrap(trailersRW.buffer(), 0, trailersRW.maxLimit()));
                    trailers = trailersRW.build();
                }

                if (valueEnvelope.isOverflowed())
                {
                    logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                }
            }

            // real headers and trailers are written contiguously starting at headersMark, reusing the
            // bytes writeEntryStart reserved (and the composed transform's envelope used as scratch during
            // the value drive) -- whatever is left over between their combined real size and the full
            // worst-case reservation is folded into the entry's own trailing paddingLen/padding fields
            logFile.writeBytes(headersMark.value, headers);
            final int trailersAt = headersMark.value + headers.sizeof();
            logFile.writeBytes(trailersAt, trailers);
            final int entryPaddingLenAt = trailersAt + trailers.sizeof();
            final int reservedEnd = headersMark.value + headersMax + trailersSizeMax + SIZEOF_PADDING_LENGTH;
            logFile.writeInt(entryPaddingLenAt, reservedEnd - entryPaddingLenAt - SIZEOF_PADDING_LENGTH);
        }
        else
        {
            final int logAvailable = logFile.available();
            final int logRequired = headers.sizeof();
            assert logAvailable >= logRequired : String.format("%s %d >= %d", headSegment, logAvailable, logRequired);

            logFile.appendBytes(headers);
            logFile.appendBytes(EMPTY_TRAILERS);
            logFile.appendInt(0);
        }

        final long offsetDelta = (int)(progress - headSegment.baseOffset());
        final long indexEntry = (offsetDelta << 32) | logFile.markValue();

        if (!headers.isEmpty())
        {
            final DirectBufferEx buffer = headers.buffer();
            final ByteBuffer byteBuffer = buffer.byteBuffer();
            assert byteBuffer != null;
            byteBuffer.clear();
            headers.forEach(h ->
            {
                final long hash = computeHash(h);
                final long hashEntry = (hash << 32) | logFile.markValue();
                hashFile.appendLong(hashEntry);
            });
        }

        if (!trailers.isEmpty())
        {
            final DirectBufferEx buffer = trailers.buffer();
            final ByteBuffer byteBuffer = buffer.byteBuffer();
            assert byteBuffer != null;
            byteBuffer.clear();
            trailers.forEach(t ->
            {
                final long hash = computeHash(t);
                final long hashEntry = (hash << 32) | logFile.markValue();
                hashFile.appendLong(hashEntry);
            });
        }

        assert indexFile.available() >= Long.BYTES;
        indexFile.appendLong(indexEntry);

        final KafkaCacheEntryFW headEntry = logFile.readBytes(logFile.markValue(), headEntryRO::wrap);

        if (deltaType == JSON_PATCH &&
            ancestorEntry != null && ancestorEntry.paddedValue().length() != -1 &&
            headEntry.paddedValue().length() != -1)
        {
            final OctetsFW ancestorValue = ancestorEntry.paddedValue().value();
            final OctetsFW headValue = headEntry.paddedValue().value();
            assert headEntry.offset$() == progress;

            final JsonProvider json = JsonProvider.provider();
            ancestorIn.wrap(ancestorValue.buffer(), ancestorValue.offset(), ancestorValue.sizeof());
            final JsonReader ancestorReader = json.createReader(ancestorIn);
            final JsonStructure ancestorJson = ancestorReader.read();
            ancestorReader.close();

            headIn.wrap(headValue.buffer(), headValue.offset(), headValue.sizeof());
            final JsonReader headReader = json.createReader(headIn);
            final JsonStructure headJson = headReader.read();
            headReader.close();

            final JsonPatch diff = json.createDiff(ancestorJson, headJson);
            final JsonArray diffJson = diff.toJsonArray();
            diffOut.wrap(diffBuffer, Integer.BYTES);
            final JsonWriter writer = json.createWriter(diffOut);
            writer.write(diffJson);
            writer.close();

            // TODO: signal delta.sizeof > head.sizeof via null delta, otherwise delta file can exceed log file

            final int deltaLength = diffOut.position();
            diffBuffer.putInt(0, deltaLength);
            deltaFile.appendBytes(diffBuffer, 0, Integer.BYTES + deltaLength);
        }

        headSegment.lastOffset(progress);
    }

    public int writeProduceEntryStart(
        long traceId,
        long bindingId,
        long authorization,
        long offset,
        Node head,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger valueLimit,
        MutableInteger trailersClaimMark,
        long timestamp,
        long ownerId,
        long producerId,
        short producerEpoch,
        int sequence,
        KafkaAckMode ackMode,
        KafkaKeyFW key,
        int valueLength,
        int valuePaddingMax,
        ArrayFW<KafkaHeaderFW> headers,
        int trailersSizeMax,
        OctetsFW payload,
        KafkaCacheModel transformKey,
        KafkaCacheModel transformValue)
    {
        assert offset > this.progress : String.format("%d > %d", offset, this.progress);
        this.progress = offset;

        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile indexFile = segment.indexFile();
        final KafkaCacheFile logFile = segment.logFile();

        final int valueMaxLength = valueLength == -1 ? 0 : valueLength;

        // a value transform's output now streams directly into this entry's own paddedValue
        // reservation below (see writeProduceEntryContinue), so this entry never has convertedFile
        // content -- convertedPosition stays NO_CONVERTED_POSITION regardless of transformValue
        final int convertedPos = NO_CONVERTED_POSITION;

        entryMark.value = logFile.capacity();

        entryInfo.putLong(FIELD_OFFSET_OFFSET, progress);
        entryInfo.putLong(FIELD_OFFSET_TIMESTAMP, timestamp);
        entryInfo.putLong(FIELD_OFFSET_OWNER_ID, ownerId);
        entryInfo.putLong(FIELD_OFFSET_ACKNOWLEDGE, NO_ACKNOWLEDGE);
        entryInfo.putLong(FIELD_OFFSET_PRODUCER_ID, producerId);
        entryInfo.putShort(FIELD_OFFSET_PRODUCER_EPOCH, producerEpoch);
        entryInfo.putInt(FIELD_OFFSET_SEQUENCE, sequence);
        entryInfo.putLong(FIELD_OFFSET_ANCESTOR, NO_ANCESTOR_OFFSET);
        entryInfo.putLong(FIELD_OFFSET_DESCENDANT, NO_DESCENDANT_OFFSET);
        entryInfo.putInt(FIELD_OFFSET_FLAGS, 0x00);
        entryInfo.putInt(FIELD_OFFSET_CONVERTED_POSITION, convertedPos);
        entryInfo.putInt(FIELD_OFFSET_DELTA_POSITION, NO_DELTA_POSITION);
        entryInfo.putShort(FIELD_OFFSET_ACK_MODE, ackMode.value());

        logFile.appendBytes(entryInfo);

        int transformed = 0;
        write:
        {
            OctetsFW value = key.value();
            if (value == null)
            {
                logFile.appendBytes(key);
                logFile.appendInt(0);
            }
            else
            {
                final int keyAt = logFile.capacity();
                Varint32FW initLength = varintRW.set(0).build();
                logFile.appendBytes(initLength);

                final KafkaCacheModel.Output writeKey = (buffer, index, length) ->
                {
                    Varint32FW progress = logFile.readBytes(keyAt, varintRO::wrap);
                    Varint32FW newLength = varintRW.set(progress.value() + length).build();
                    int keyShift = newLength.sizeof() - progress.sizeof();
                    if (keyShift > 0)
                    {
                        OctetsFW octets = logFile
                            .readBytes(progress.limit(), progress.limit() + progress.value(), octetsRO::wrap);
                        logFile.writeBytes(newLength.limit(), octets);

                        logFile.advance(keyAt + newLength.limit());
                    }
                    logFile.writeBytes(keyAt, newLength);
                    logFile.appendBytes(buffer, index, length);
                };

                transformed = transformKey.transform(traceId, bindingId, authorization,
                    value.buffer(), value.offset(), value.limit(), writeKey);

                if (transformed == -1)
                {
                    break write;
                }
                logFile.appendInt(0);
            }
            logFile.appendInt(valueLength);

            valueMark.value = logFile.capacity();
            valueLimit.value = valueMark.value;

            final int paddingLenAt = valueMark.value + valueMaxLength;
            final int logAvailable = logFile.available() - valueMaxLength - SIZEOF_PADDING_LENGTH - valuePaddingMax;
            final int logRequired = headers.sizeof();
            assert logAvailable >= logRequired : String.format("%s %d >= %d", segment, logAvailable, logRequired);
            logFile.advance(paddingLenAt + SIZEOF_PADDING_LENGTH + valuePaddingMax);
            logFile.writeInt(paddingLenAt, valuePaddingMax);
            logFile.appendBytes(headers);

            final int trailersAt = logFile.capacity();
            logFile.advance(logFile.capacity() + trailersSizeMax + SIZEOF_PADDING_LENGTH);
            logFile.writeBytes(trailersAt, EMPTY_TRAILERS); // needed for incomplete tryWrap
            logFile.writeInt(trailersAt + SIZEOF_EMPTY_TRAILERS, trailersSizeMax - SIZEOF_EMPTY_TRAILERS);

            // a second, separate claim: a block for a composed transform's envelope.set() calls during
            // encode, written directly rather than staged in a heap-resident arena; consumed via
            // KafkaCacheTrailerEnvelope.writeHeaders(...) before the claim above is overwritten with the
            // final merged trailers
            trailersClaimMark.value = logFile.capacity();
            logFile.advance(trailersClaimMark.value + trailersSizeMax);

            final long offsetDelta = (int)(progress - segment.baseOffset());
            final long indexEntry = (offsetDelta << 32) | entryMark.value;
            assert indexFile.available() >= Long.BYTES;
            indexFile.appendLong(indexEntry);
        }
        return transformed;
    }

    public int writeProduceEntryContinue(
        long traceId,
        long bindingId,
        long authorization,
        int flags,
        Node head,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger valueLimit,
        OctetsFW payload,
        KafkaCacheModel transformValue)
    {
        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile logFile = segment.logFile();

        int transformed = 0;
        if (payload != null)
        {
            if (transformValue == KafkaCacheModel.NONE)
            {
                valueLimit.value += logFile.writeBytes(valueLimit.value, payload);
            }
            else
            {
                // re-derives the reservation's total capacity from the placeholder length/paddingLen
                // fields writeProduceEntryStart already wrote -- both still hold their original,
                // provisional values until the COMPLETE branch below finalizes them, so this is stable
                // across every fragment of the same value
                final int valueMaxLength = Math.max(logFile.readInt(valueMark.value - SIZE_OF_INT), 0);
                final int paddingLenAt = valueMark.value + valueMaxLength;
                final int valuePaddingMax = logFile.readInt(paddingLenAt);
                final int reservedMax = valueMaxLength + valuePaddingMax;

                final KafkaCacheModel.Output consumeTransformed = (buffer, index, length) ->
                {
                    final int written = valueLimit.value - valueMark.value;
                    if (written + length > reservedMax)
                    {
                        logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                    }
                    else
                    {
                        logFile.writeBytes(valueLimit.value, buffer, index, length);
                        valueLimit.value += length;
                    }
                };

                final KafkaCacheModel.Result result = transformValue.transform(traceId, bindingId, authorization, flags,
                    payload.buffer(), payload.offset(), payload.limit(), consumeTransformed);

                if (result.status() == ModelStatus.REJECTED)
                {
                    transformed = -1;
                    logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                }
                else if (result.status() == ModelStatus.COMPLETE)
                {
                    // the transformed value's real length rarely matches the reservation sized from the
                    // raw value -- finalize length in place and relocate paddingLen to absorb whatever
                    // slack remains, exactly as commitKeyOverride does for a transformed key
                    final int actualLength = valueLimit.value - valueMark.value;
                    final int finalPaddingLen = reservedMax - actualLength;
                    logFile.writeInt(valueMark.value - SIZE_OF_INT, actualLength);
                    logFile.writeInt(valueLimit.value, finalPaddingLen);
                }
            }
        }

        return transformed;
    }

    public void writeProduceEntryFin(
        Node head,
        MutableInteger entryMark,
        MutableInteger valueLimit,
        long acknowledge,
        Array32FW<KafkaHeaderFW> trailers,
        boolean trailersOverflowed)
    {
        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile logFile = segment.logFile();

        final int valuePaddingMax = logFile.readInt(valueLimit.value);
        valueLimit.value += SIZEOF_PADDING_LENGTH + valuePaddingMax;

        final  Array32FW<KafkaHeaderFW> headers = logFile.readBytes(valueLimit.value, headersRO::wrap);
        valueLimit.value += headers.sizeof();

        final int trailersAt = valueLimit.value;
        final int trailersSizeMax = SIZEOF_EMPTY_TRAILERS + logFile.readInt(trailersAt + SIZEOF_EMPTY_TRAILERS);

        if (!trailers.isEmpty())
        {
            logFile.writeBytes(valueLimit.value, trailers);
            valueLimit.value += trailers.sizeof();
            logFile.writeInt(valueLimit.value, trailersSizeMax - trailers.sizeof());
        }

        valueLimit.value = trailersAt + trailersSizeMax;

        logFile.writeLong(entryMark.value + FIELD_OFFSET_ACKNOWLEDGE, acknowledge);
        logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS,
            trailersOverflowed ? CACHE_ENTRY_FLAGS_ABORTED : CACHE_ENTRY_FLAGS_COMPLETED);
    }

    public long retainAt(
        KafkaCacheSegment segment)
    {
        return segment.timestamp() + config.segmentMillis;
    }

    public long deleteAt(
        KafkaCacheSegment segment,
        long retentionMillisMax)
    {
        return segment.timestamp() + Math.min(config.retentionMillis, retentionMillisMax);
    }

    public long compactAt(
        KafkaCacheSegment segment)
    {
        final long dirtySince = segment.dirtySince();

        long cleanableAt = segment.cleanableAt();
        if (cleanableAt == Long.MAX_VALUE && dirtySince != NO_DIRTY_SINCE)
        {
            final double cleanableDirtyRatio = segment.cleanableDirtyRatio();
            if (cleanableDirtyRatio >= config.minCleanableDirtyRatio)
            {
                final long now = System.currentTimeMillis();

                cleanableAt = Math.min(dirtySince + config.minCompactionLagMillis, now);
            }
            else if (cleanableDirtyRatio != 0.0 && config.maxCompactionLagMillis != Long.MAX_VALUE)
            {
                final long now = System.currentTimeMillis();

                cleanableAt = Math.min(dirtySince + config.maxCompactionLagMillis, now);
            }

            if (cleanableAt != Long.MAX_VALUE)
            {
                segment.cleanableAt(cleanableAt);
            }
        }

        return cleanableAt;
    }

    public KafkaCacheCleanupPolicy cleanupPolicy()
    {
        return config.cleanupPolicy;
    }

    public long computeKeyHash(
        KafkaKeyFW key)
    {
        return computeHash(key);
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s[%d]", cache, topic, id);
    }

    private long computeHash(
        Flyweight keyOrHeader)
    {
        // TODO: compute null key hash in advance
        final DirectBufferEx buffer = keyOrHeader.buffer();
        final ByteBuffer byteBuffer = buffer.byteBuffer();
        byteBuffer.clear();
        assert byteBuffer != null;
        checksum.reset();
        byteBuffer.position(keyOrHeader.offset());
        byteBuffer.limit(keyOrHeader.limit());
        checksum.update(byteBuffer);
        return checksum.getValue();
    }

    // Overrides the already-reserved padded-key region in place with envelope's extracted key, in place of
    // KafkaPipeline's old SWITCH_KEY lane and KafkaEntrySink's single-slot buffering. The override must fit
    // within the padding the key's own model already reserved -- the log file only grows forward, so there
    // is nowhere else for a larger key to go. Returns false when it doesn't fit, so the caller aborts the
    // entry rather than writing a corrupt padded key, mirroring how a transformed value exceeding its own
    // reservation is handled elsewhere in this file.
    private boolean commitKeyOverride(
        KafkaCacheFile logFile,
        int entryAt,
        DirectBufferEx override)
    {
        final int position = entryAt + FIELD_OFFSET_PADDED_KEY;
        final KafkaCachePaddedKeyFW paddedKey = logFile.readBytes(position, paddedKeyRO::wrap);
        final int paddedKeySize = paddedKey.sizeof();
        final int overrideLength = override.capacity();
        final KafkaCachePaddedKeyFW.Builder paddedKeyBuilder = paddedKeyRW;
        final int keySize = paddedKeyBuilder
            .key(k -> k.length(overrideLength).value(override, 0, overrideLength)).sizeof();
        final int padding = paddedKeySize - keySize - SIZE_OF_INT;

        final boolean fits = padding >= 0;
        if (fits)
        {
            paddedKeyBuilder.padding(logFile.buffer(), 0, padding);
            final KafkaCachePaddedKeyFW newPaddedKey = paddedKeyBuilder.build();
            logFile.writeBytes(position, newPaddedKey.buffer(), newPaddedKey.offset(), newPaddedKey.sizeof());
        }
        return fits;
    }

    public final class Node
    {
        private volatile KafkaCacheSegment segment;
        private volatile KafkaCachePartition.Node previous;
        private volatile KafkaCachePartition.Node next;

        Node()
        {
            this.segment = null;
            this.previous = this;
            this.next = this;
        }

        Node(
            KafkaCacheSegment segment)
        {
            this.segment = requireNonNull(segment);
            this.previous = sentinel;
            this.next = sentinel;
        }

        public boolean sentinel()
        {
            return this == sentinel;
        }

        public Node previous()
        {
            return previous;
        }

        public Node next()
        {
            return next;
        }

        public KafkaCacheSegment segment()
        {
            return segment;
        }

        public Node seekAncestor(
            long baseOffset)
        {
            Node ancestorNode = this;

            while (!ancestorNode.sentinel() && ancestorNode.segment.baseOffset() > baseOffset)
            {
                ancestorNode = ancestorNode.previous;
            }

            return ancestorNode;
        }

        public void remove()
        {
            assert segment != null;
            segment.delete();
            segment.close();

            next.previous = previous;
            previous.next = next;
        }

        public void segment(
            KafkaCacheSegment segment)
        {
            assert segment != null;
            this.segment.close();
            this.segment = segment;
        }

        public void clean(
            long now)
        {
            assert next != sentinel; // not head segment

            if (segment.cleanableAt() <= now)
            {
                // TODO: use temporary files plus move to avoid corrupted log on restart
                segment.delete();

                final KafkaCacheSegment appender = new KafkaCacheSegment(segment, config, appendBuf, sortSpaceRef);
                final KafkaCacheFile logFile = segment.logFile();
                final KafkaCacheFile deltaFile = segment.deltaFile();

                for (int logPosition = 0; logPosition < logFile.capacity(); )
                {
                    final KafkaCacheEntryFW logEntry = logFile.readBytes(logPosition, logEntryRO::wrap);
                    if ((logEntry.flags() & CACHE_ENTRY_FLAGS_DIRTY) == 0)
                    {
                        final long logOffset = logEntry.offset$();
                        final KafkaKeyFW key = logEntry.paddedKey().key();
                        final ArrayFW<KafkaHeaderFW> headers = logEntry.headers();
                        final int deltaPosition = logEntry.deltaPosition();
                        final long keyHash = computeHash(key);

                        final long offsetDelta = (int)(logOffset - segment.baseOffset());
                        final long indexEntry = (offsetDelta << 32) | appender.logFile().capacity();
                        appender.indexFile().appendLong(indexEntry);

                        final long keyHashEntry = keyHash << 32 | appender.logFile().capacity();
                        appender.hashFile().appendLong(keyHashEntry);

                        headers.forEach(header ->
                        {
                            final long headerHash = computeHash(header);
                            final long headerHashEntry = headerHash << 32 | appender.logFile().capacity();
                            appender.hashFile().appendLong(headerHashEntry);
                        });

                        appender.logFile().appendBytes(logEntry);
                        if (deltaPosition != -1)
                        {
                            final int newLogEntryAt = appender.logFile().capacity() - logEntry.sizeof();
                            appender.logFile().writeInt(newLogEntryAt + FIELD_OFFSET_DELTA_POSITION, deltaFile.capacity());

                            final KafkaCacheDeltaFW deltaEntry = deltaFile.readBytes(deltaPosition, deltaEntryRO::wrap);
                            appender.deltaFile().appendBytes(deltaEntry);
                        }

                        // note: keys cleanup must also retain non-zero base offsets when spanning multiple segments
                        final int deltaBaseOffset = 0;
                        final long keyEntry = keyHash << 32 | deltaBaseOffset;
                        appender.keysFile().appendLong(keyEntry);

                        appender.lastOffset(logOffset);
                    }

                    logPosition = logEntry.limit();
                }

                segment.close();

                final KafkaCacheSegment frozen = appender.freeze();
                appender.close();

                if (frozen.logFile().empty())
                {
                    frozen.delete();
                    remove();
                }
                else
                {
                    segment(frozen);
                }
            }
        }

        public void findAndAbortProducerId(
            long producerId,
            KafkaCacheEntryFW cacheEntry)
        {
            final KafkaCacheFile logFile = segment.logFile();

            for (int offsetBytes = 0; offsetBytes < logFile.capacity(); offsetBytes = cacheEntry.limit())
            {
                final KafkaCacheEntryFW entry = logFile.readBytes(offsetBytes, cacheEntry::wrap);
                if (entry.ownerId() == producerId && (entry.flags() & CACHE_ENTRY_FLAGS_CONTROL) == 0x00)
                {
                    logFile.writeInt(entry.offset() + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                }
            }
        }

        public KafkaCacheEntryFW findAndMarkAncestor(
            KafkaKeyFW key,
            long hash,
            long descendantOffset,
            KafkaCacheEntryFW ancestorEntry)
        {
            KafkaCacheEntryFW ancestor = null;

            ancestor:
            if (key.length() != -1)
            {
                final KafkaCacheIndexFile hashFile = segment.hashFile();
                final KafkaCacheFile logFile = segment.logFile();
                long hashCursor = hashFile.last((int) hash);
                int position = cursorValue(hashCursor);
                while (position != NEXT_SEGMENT_VALUE && position != RETRY_SEGMENT_VALUE)
                {
                    final KafkaCacheEntryFW cacheEntry = logFile.readBytes(position, ancestorEntry::wrap);
                    assert cacheEntry != null;
                    if (!isAbortedEntry(cacheEntry) &&
                        !isControlEntry(cacheEntry) &&
                        key.equals(cacheEntry.paddedKey().key()))
                    {
                        ancestor = cacheEntry;
                        markDescendantAndDirty(ancestor, descendantOffset);
                        break ancestor;
                    }
                    hashCursor = hashFile.lower((int) hash, hashCursor);
                    position = cursorValue(hashCursor);
                }
                assert position == NEXT_SEGMENT_VALUE || position == RETRY_SEGMENT_VALUE;
            }

            return ancestor;
        }

        public KafkaCacheEntryFW findAndMarkDirty(
            KafkaCacheEntryFW dirty,
            long partitionOffset)
        {
            final int offsetDelta = (int)(partitionOffset - segment.baseOffset());
            final long cursor = segment.indexFile().first((int) offsetDelta);
            final int position = KafkaCacheCursorRecord.cursorValue(cursor);

            final KafkaCacheFile logFile = segment.logFile();
            final KafkaCacheEntryFW dirtyEntry = logFile.readBytes(position, dirty::tryWrap);
            assert dirtyEntry != null;

            markDirty(dirtyEntry);

            return dirtyEntry;
        }

        private void markDescendantAndDirty(
            KafkaCacheEntryFW ancestor,
            long descendantOffset)
        {
            final KafkaCacheFile logFile = segment.logFile();
            logFile.writeLong(ancestor.offset() + FIELD_OFFSET_DESCENDANT, descendantOffset);
            logFile.writeInt(ancestor.offset() + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_DIRTY);
            segment.markDirtyBytes(ancestor.sizeof());
        }

        public void markDirty(
            KafkaCacheEntryFW entry)
        {
            final KafkaCacheFile logFile = segment.logFile();
            logFile.writeInt(entry.offset() + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_DIRTY);
            segment.markDirtyBytes(entry.sizeof());
        }

        @Override
        public String toString()
        {
            Function<KafkaCacheSegment, String> baseOffset = s -> s != null ? Long.toString(s.baseOffset()) : "sentinel";
            return String.format("[%s] %s", getClass().getSimpleName(), baseOffset.apply(segment));
        }

        private boolean isControlEntry(
            KafkaCacheEntryFW cacheEntry)
        {
            return (cacheEntry.flags() & CACHE_ENTRY_FLAGS_CONTROL) != 0;
        }

        private boolean isAbortedEntry(
            KafkaCacheEntryFW cacheEntry)
        {
            return (cacheEntry.flags() & CACHE_ENTRY_FLAGS_ABORTED) != 0;
        }
    }

    private static Path createDirectories(
        Path directory)
    {
        try
        {
            Files.createDirectories(directory);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return directory;
    }
}
