/*
 * Copyright 2021-2023 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_KEY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_OFFSET;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW.FIELD_OFFSET_OWNER_ID;
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

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;
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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheDeltaFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public final class KafkaCachePartition
{
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
    public static final int CACHE_ENTRY_FLAGS_DIRTY = 0x01;
    public static final int CACHE_ENTRY_FLAGS_COMPLETED = 0x02;
    public static final int CACHE_ENTRY_FLAGS_ABORTED = 0x04;
    public static final int CACHE_ENTRY_FLAGS_CONTROL = 0x08;
    public static final int CACHE_ENTRY_FLAGS_ADVANCE = CACHE_ENTRY_FLAGS_COMPLETED | CACHE_ENTRY_FLAGS_DIRTY;

    private static final long OFFSET_HISTORICAL = KafkaOffsetType.HISTORICAL.value();

    private static final Array32FW<KafkaHeaderFW> EMPTY_TRAILERS =
            new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(new UnsafeBuffer(new byte[8]), 0, 8)
                .build();
    private static final int SIZEOF_EMPTY_TRAILERS = EMPTY_TRAILERS.sizeof();

    private static final int SIZEOF_PADDING_LENGTH = Integer.BYTES;

    private final KafkaCacheEntryFW headEntryRO = new KafkaCacheEntryFW();
    private final KafkaCacheEntryFW logEntryRO = new KafkaCacheEntryFW();
    private final KafkaCacheDeltaFW deltaEntryRO = new KafkaCacheDeltaFW();

    private final MutableDirectBuffer entryInfo = new UnsafeBuffer(new byte[FIELD_OFFSET_KEY]);
    private final MutableDirectBuffer valueInfo = new UnsafeBuffer(new byte[Integer.BYTES]);

    private final Varint32FW.Builder varIntRW = new Varint32FW.Builder().wrap(new UnsafeBuffer(new byte[5]), 0, 5);
    private final Array32FW<KafkaHeaderFW> headersRO = new Array32FW<KafkaHeaderFW>(new KafkaHeaderFW());

    private final DirectBufferInputStream ancestorIn = new DirectBufferInputStream();
    private final DirectBufferInputStream headIn = new DirectBufferInputStream();
    private final MutableDirectBuffer diffBuffer = new ExpandableArrayBuffer();
    private final ExpandableDirectBufferOutputStream diffOut = new ExpandableDirectBufferOutputStream();

    private final Path location;
    private final KafkaCacheTopicConfig config;
    private final String cache;
    private final String topic;
    private final int id;
    private final MutableDirectBuffer appendBuf;
    private final IntFunction<long[]> sortSpaceRef;
    private final Node sentinel;
    private final CRC32C checksum;

    private long progress;

    private KafkaCacheEntryFW ancestorEntry;
    private final AtomicLong produceCapacity;

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
        this.appendBuf = new UnsafeBuffer(allocateDirect(appendCapacity));
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
        this.appendBuf = new UnsafeBuffer(allocateDirect(appendCapacity));
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
                    Math.max(valueLength, 0) + headersSizeMax;
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
        long offset,
        MutableInteger entryMark,
        MutableInteger valueMark,
        long timestamp,
        long producerId,
        KafkaKeyFW key,
        ArrayFW<KafkaHeaderFW> headers,
        OctetsFW value,
        KafkaCacheEntryFW ancestor,
        int entryFlags,
        KafkaDeltaType deltaType,
        ConverterHandler convertKey,
        ConverterHandler convertValue,
        String bindingName)
    {
        final long keyHash = computeHash(key);
        final int valueLength = value != null ? value.sizeof() : -1;
        writeEntryStart(offset, entryMark, valueMark, timestamp, producerId, key,
            keyHash, valueLength, ancestor, entryFlags, deltaType, value, convertKey, convertValue, bindingName);
        writeEntryContinue(FLAGS_COMPLETE, offset, entryMark, valueMark, value, convertValue, bindingName);
        writeEntryFinish(headers, deltaType);
    }

    public void writeEntryStart(
        long offset,
        MutableInteger entryMark,
        MutableInteger valueMark,
        long timestamp,
        long producerId,
        KafkaKeyFW key,
        long keyHash,
        int valueLength,
        KafkaCacheEntryFW ancestor,
        int entryFlags,
        KafkaDeltaType deltaType,
        OctetsFW payload,
        ConverterHandler convertKey,
        ConverterHandler convertValue,
        String bindingName)
    {
        assert offset > this.progress : String.format("%d > %d", offset, this.progress);
        this.progress = offset;

        final Node head = sentinel.previous;
        assert head != sentinel;

        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile logFile = segment.logFile();
        final KafkaCacheFile deltaFile = segment.deltaFile();
        final KafkaCacheFile hashFile = segment.hashFile();
        final KafkaCacheFile keysFile = segment.keysFile();
        final KafkaCacheFile nullsFile = segment.nullsFile();
        final KafkaCacheFile convertedFile = segment.convertedFile();

        final int valueMaxLength = valueLength == -1 ? 0 : valueLength;

        logFile.mark();

        final long ancestorOffset = ancestor != null ? ancestor.offset$() : NO_ANCESTOR_OFFSET;
        final int deltaPosition = deltaType == JSON_PATCH &&
                                  ancestor != null && ancestor.valueLen() != -1 &&
                                  valueLength != -1
                    ? deltaFile.capacity()
                    : NO_DELTA_POSITION;

        assert deltaPosition == NO_DELTA_POSITION || ancestor != null;
        this.ancestorEntry = ancestor;

        int convertedPos = NO_CONVERTED_POSITION;
        if (convertValue != ConverterHandler.NONE)
        {
            int convertedPadding = convertValue.padding(payload.buffer(), payload.offset(), payload.sizeof());
            int convertedMaxLength = valueMaxLength + convertedPadding;

            convertedPos = convertedFile.capacity();
            convertedFile.advance(convertedPos + convertedMaxLength + SIZE_OF_INT * 2);

            convertedFile.writeInt(convertedPos, 0); // length
            convertedFile.writeInt(convertedPos + SIZE_OF_INT, convertedMaxLength); // padding
        }

        entryMark.value = logFile.capacity();

        entryInfo.putLong(FIELD_OFFSET_OFFSET, progress);
        entryInfo.putLong(FIELD_OFFSET_TIMESTAMP, timestamp);
        entryInfo.putLong(FIELD_OFFSET_OWNER_ID, producerId);
        entryInfo.putLong(FIELD_OFFSET_ACKNOWLEDGE, NO_ACKNOWLEDGE);
        entryInfo.putInt(FIELD_OFFSET_SEQUENCE, NO_SEQUENCE);
        entryInfo.putLong(FIELD_OFFSET_ANCESTOR, ancestorOffset);
        entryInfo.putLong(FIELD_OFFSET_DESCENDANT, NO_DESCENDANT_OFFSET);
        entryInfo.putInt(FIELD_OFFSET_FLAGS, entryFlags);
        entryInfo.putInt(FIELD_OFFSET_CONVERTED_POSITION, convertedPos);
        entryInfo.putInt(FIELD_OFFSET_DELTA_POSITION, deltaPosition);
        entryInfo.putShort(FIELD_OFFSET_ACK_MODE, KafkaAckMode.NONE.value());

        logFile.appendBytes(entryInfo);
        if (key.value() == null)
        {
            logFile.appendBytes(key);
        }
        else
        {
            final ValueConsumer writeKey = (buffer, index, length) ->
            {
                Varint32FW newLength = varIntRW.set(length).build();
                logFile.appendBytes(newLength);
                logFile.appendBytes(buffer, index, length);
            };
            OctetsFW value = key.value();
            int converted = convertKey.convert(value.buffer(), value.offset(), value.sizeof(), writeKey);
            if (converted == -1)
            {
                logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                System.out.println(String.format("%s %s: Skipping invalid message on topic %s, partition %d, offset %d",
                        System.currentTimeMillis(), bindingName, topic, id, offset));
            }
        }
        logFile.appendInt(valueLength);

        valueMark.value = logFile.capacity();

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

    public void writeEntryContinue(
        int flags,
        long offset,
        MutableInteger entryMark,
        MutableInteger valueMark,
        OctetsFW payload,
        ConverterHandler convertValue,
        String bindingName)
    {
        final Node head = sentinel.previous;
        assert head != sentinel;

        final KafkaCacheSegment headSegment = head.segment;
        assert headSegment != null;

        final KafkaCacheFile logFile = headSegment.logFile();
        final KafkaCacheFile convertedFile = headSegment.convertedFile();

        final int logAvailable = logFile.available();
        final int logRequired = payload.sizeof();
        assert logAvailable >= logRequired;

        logFile.appendBytes(payload.buffer(), payload.offset(), payload.sizeof());

        if (payload != null && convertValue != ConverterHandler.NONE)
        {
            final ValueConsumer consumeConverted = (buffer, index, length) ->
            {
                final int convertedLengthAt = logFile.readInt(entryMark.value + FIELD_OFFSET_CONVERTED_POSITION);
                final int convertedLength = convertedFile.readInt(convertedLengthAt);
                final int convertedValueLimit = convertedLengthAt + SIZE_OF_INT + convertedLength;
                final int convertedPadding = convertedFile.readInt(convertedValueLimit);

                assert convertedPadding - length >= 0;

                convertedFile.writeInt(convertedLengthAt, convertedLength + length);
                convertedFile.writeBytes(convertedValueLimit, buffer, index, length);
                convertedFile.writeInt(convertedValueLimit + length, convertedPadding - length);
            };

            final int valueLength = logFile.capacity() - valueMark.value;
            int entryFlags = logFile.readInt(entryMark.value + FIELD_OFFSET_FLAGS);

            if ((flags & FLAGS_FIN) != 0x00 && (entryFlags & CACHE_ENTRY_FLAGS_ABORTED) == 0x00)
            {
                int converted = convertValue.convert(logFile.buffer(), valueMark.value, valueLength, consumeConverted);
                if (converted == -1)
                {
                    logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_ABORTED);
                    System.out.println(String.format("%s %s: Skipping invalid message on topic %s, partition %d, offset %d",
                        System.currentTimeMillis(), bindingName, topic, id, offset));
                }
            }
        }
    }

    public void writeEntryFinish(
        ArrayFW<KafkaHeaderFW> headers,
        KafkaDeltaType deltaType)
    {
        final Node head = sentinel.previous;
        assert head != sentinel;

        final KafkaCacheSegment headSegment = head.segment;
        assert headSegment != null;

        final KafkaCacheFile logFile = headSegment.logFile();
        final KafkaCacheFile deltaFile = headSegment.deltaFile();
        final KafkaCacheFile hashFile = headSegment.hashFile();
        final KafkaCacheFile indexFile = headSegment.indexFile();

        final int logAvailable = logFile.available();
        final int logRequired = headers.sizeof();
        assert logAvailable >= logRequired : String.format("%s %d >= %d", headSegment, logAvailable, logRequired);

        logFile.appendBytes(headers);
        logFile.appendBytes(EMPTY_TRAILERS);
        logFile.appendInt(0);

        final long offsetDelta = (int)(progress - headSegment.baseOffset());
        final long indexEntry = (offsetDelta << 32) | logFile.markValue();

        if (!headers.isEmpty())
        {
            final DirectBuffer buffer = headers.buffer();
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

        assert indexFile.available() >= Long.BYTES;
        indexFile.appendLong(indexEntry);

        final KafkaCacheEntryFW headEntry = logFile.readBytes(logFile.markValue(), headEntryRO::wrap);

        if (deltaType == JSON_PATCH &&
            ancestorEntry != null && ancestorEntry.valueLen() != -1 &&
            headEntry.valueLen() != -1)
        {
            final OctetsFW ancestorValue = ancestorEntry.value();
            final OctetsFW headValue = headEntry.value();
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
        long offset,
        Node head,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger valueLimit,
        long timestamp,
        long ownerId,
        int sequence,
        KafkaAckMode ackMode,
        KafkaKeyFW key,
        long keyHash,
        int valueLength,
        ArrayFW<KafkaHeaderFW> headers,
        int trailersSizeMax,
        OctetsFW payload,
        ConverterHandler convertKey,
        ConverterHandler convertValue)
    {
        assert offset > this.progress : String.format("%d > %d", offset, this.progress);
        this.progress = offset;

        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile indexFile = segment.indexFile();
        final KafkaCacheFile logFile = segment.logFile();
        final KafkaCacheFile convertedFile = segment.convertedFile();

        final int valueMaxLength = valueLength == -1 ? 0 : valueLength;

        int convertedPos = NO_CONVERTED_POSITION;
        if (convertValue != ConverterHandler.NONE)
        {
            int convertedPadding = convertValue.padding(payload.buffer(), payload.offset(), payload.sizeof());
            int convertedMaxLength = valueMaxLength + convertedPadding;

            convertedPos = convertedFile.capacity();
            convertedFile.advance(convertedPos + convertedMaxLength + SIZE_OF_INT * 2);

            convertedFile.writeInt(convertedPos, 0); // length
            convertedFile.writeInt(convertedPos + SIZE_OF_INT, convertedMaxLength); // padding
        }

        entryMark.value = logFile.capacity();

        entryInfo.putLong(FIELD_OFFSET_OFFSET, progress);
        entryInfo.putLong(FIELD_OFFSET_TIMESTAMP, timestamp);
        entryInfo.putLong(FIELD_OFFSET_OWNER_ID, ownerId);
        entryInfo.putLong(FIELD_OFFSET_ACKNOWLEDGE, NO_ACKNOWLEDGE);
        entryInfo.putInt(FIELD_OFFSET_SEQUENCE, sequence);
        entryInfo.putLong(FIELD_OFFSET_ANCESTOR, NO_ANCESTOR_OFFSET);
        entryInfo.putLong(FIELD_OFFSET_DESCENDANT, NO_DESCENDANT_OFFSET);
        entryInfo.putInt(FIELD_OFFSET_FLAGS, 0x00);
        entryInfo.putInt(FIELD_OFFSET_CONVERTED_POSITION, convertedPos);
        entryInfo.putInt(FIELD_OFFSET_DELTA_POSITION, NO_DELTA_POSITION);
        entryInfo.putShort(FIELD_OFFSET_ACK_MODE, ackMode.value());

        logFile.appendBytes(entryInfo);

        int converted = 0;
        write:
        {
            OctetsFW value = key.value();
            if (value == null)
            {
                logFile.appendBytes(key);
            }
            else
            {
                final ValueConsumer writeKey = (buffer, index, length) ->
                {
                    Varint32FW newLength = varIntRW.set(length).build();
                    logFile.appendBytes(newLength);
                    logFile.appendBytes(buffer, index, length);
                };

                converted = convertKey.convert(value.buffer(), value.offset(), value.sizeof(), writeKey);

                if (converted == -1)
                {
                    break write;
                }
            }
            logFile.appendInt(valueLength);

            valueMark.value = logFile.capacity();
            valueLimit.value = valueMark.value;

            final int logAvailable = logFile.available() - valueMaxLength;
            final int logRequired = headers.sizeof();
            assert logAvailable >= logRequired : String.format("%s %d >= %d", segment, logAvailable, logRequired);
            logFile.advance(valueMark.value + valueMaxLength);
            logFile.appendBytes(headers);

            final int trailersAt = logFile.capacity();
            logFile.advance(logFile.capacity() + trailersSizeMax + SIZEOF_PADDING_LENGTH);
            logFile.writeBytes(trailersAt, EMPTY_TRAILERS); // needed for incomplete tryWrap
            logFile.writeInt(trailersAt + SIZEOF_EMPTY_TRAILERS, trailersSizeMax - SIZEOF_EMPTY_TRAILERS);

            final long offsetDelta = (int)(progress - segment.baseOffset());
            final long indexEntry = (offsetDelta << 32) | entryMark.value;
            assert indexFile.available() >= Long.BYTES;
            indexFile.appendLong(indexEntry);
        }
        return converted;
    }

    public int writeProduceEntryContinue(
        int flags,
        Node head,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableInteger valueLimit,
        OctetsFW payload,
        ConverterHandler convertValue)
    {
        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile logFile = segment.logFile();
        final KafkaCacheFile convertedFile = segment.convertedFile();

        int converted = 0;
        if (payload != null)
        {
            valueLimit.value += logFile.writeBytes(valueLimit.value, payload);

            if (convertValue != ConverterHandler.NONE)
            {
                final ValueConsumer consumeConverted = (buffer, index, length) ->
                {
                    final int convertedLengthAt = logFile.readInt(entryMark.value + FIELD_OFFSET_CONVERTED_POSITION);
                    final int convertedLength = convertedFile.readInt(convertedLengthAt);
                    final int convertedValueLimit = convertedLengthAt + SIZE_OF_INT + convertedLength;
                    final int convertedPadding = convertedFile.readInt(convertedValueLimit);

                    assert convertedPadding - length >= 0;

                    convertedFile.writeInt(convertedLengthAt, convertedLength + length);
                    convertedFile.writeBytes(convertedValueLimit, buffer, index, length);
                    convertedFile.writeInt(convertedValueLimit + length, convertedPadding - length);
                };

                final int valueLength = valueLimit.value - valueMark.value;
                if ((flags & FLAGS_FIN) != 0x00)
                {
                    converted = convertValue.convert(logFile.buffer(), valueMark.value, valueLength, consumeConverted);
                }
            }
        }

        return converted;
    }

    public void writeProduceEntryFin(
        Node head,
        MutableInteger entryMark,
        MutableInteger valueLimit,
        long acknowledge,
        Array32FW<KafkaHeaderFW> trailers)
    {
        final KafkaCacheSegment segment = head.segment;
        assert segment != null;

        final KafkaCacheFile logFile = segment.logFile();

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
        logFile.writeInt(entryMark.value + FIELD_OFFSET_FLAGS, CACHE_ENTRY_FLAGS_COMPLETED);
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
        final DirectBuffer buffer = keyOrHeader.buffer();
        final ByteBuffer byteBuffer = buffer.byteBuffer();
        byteBuffer.clear();
        assert byteBuffer != null;
        checksum.reset();
        byteBuffer.position(keyOrHeader.offset());
        byteBuffer.limit(keyOrHeader.limit());
        checksum.update(byteBuffer);
        return checksum.getValue();
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
                        final KafkaKeyFW key = logEntry.key();
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
                        key.equals(cacheEntry.key()))
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
