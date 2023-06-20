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
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursor;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorIndex;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorRetryValue;
import static io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorRecord.cursorValue;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaSkip.SKIP_MANY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW.KIND_SKIP;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW.KIND_VALUE;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongHashSet;
import org.agrona.collections.MutableInteger;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeadersFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaNotFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheDeltaFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;

public final class KafkaCacheCursorFactory
{
    private final KafkaCacheDeltaFW deltaRO = new KafkaCacheDeltaFW();
    private final KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();
    private final KafkaHeaderFW headerRO = new KafkaHeaderFW();

    private final MutableDirectBuffer writeBuffer;
    private final CRC32C checksum;

    public static final int POSITION_UNSET = -1;
    public static final int INDEX_UNSET = -1;

    public KafkaCacheCursorFactory(
        MutableDirectBuffer writeBuffer)
    {
        this.writeBuffer = writeBuffer;
        this.checksum = new CRC32C();
    }

    public KafkaCacheCursor newCursor(
        KafkaFilterCondition condition,
        KafkaDeltaType deltaType)
    {
        return new KafkaCacheCursor(condition, deltaType);
    }

    public final class KafkaCacheCursor implements AutoCloseable
    {
        private final KafkaDeltaType deltaType;
        private final LongHashSet deltaKeyOffsets; // TODO: bounded LongHashCache, evict -> discard
        private final KafkaFilterCondition condition;

        private Node segmentNode;
        private KafkaCacheSegment segment;

        public long filters;
        public long offset;
        private long latestOffset;
        private int position;

        KafkaCacheCursor(
            KafkaFilterCondition condition,
            KafkaDeltaType deltaType)
        {
            this.condition = condition;
            this.deltaType = deltaType;
            this.deltaKeyOffsets = new LongHashSet();
        }

        public void init(
            KafkaCacheCursor cursor)
        {
            init(cursor.segmentNode, cursor.offset, cursor.latestOffset);
        }

        public void init(
            Node segmentNode,
            long offset,
            long latestOffset)
        {
            assert this.segmentNode == null;
            assert this.segment == null;

            this.offset = offset;
            this.latestOffset = latestOffset;

            assert !segmentNode.sentinel();
            KafkaCacheSegment newSegment = null;
            while (newSegment == null)
            {
                newSegment = segmentNode.segment().acquire();
                if (newSegment == null)
                {
                    segmentNode = segmentNode.next();
                }
            }
            this.segmentNode = segmentNode;
            this.segment = newSegment;

            assert this.segmentNode != null;
            assert this.segment != null;

            final int position = condition.reset(segment, offset, latestOffset, POSITION_UNSET);
            this.position = position == RETRY_SEGMENT_VALUE || position == NEXT_SEGMENT_VALUE ? 0 : position;
        }

        public KafkaCacheEntryFW next(
            KafkaCacheEntryFW cacheEntry)
        {
            KafkaCacheEntryFW nextEntry = null;

            next:
            while (nextEntry == null)
            {
                final int positionNext = condition.next(position);
                if (positionNext == RETRY_SEGMENT_VALUE)
                {
                    break next;
                }

                if (positionNext == NEXT_SEGMENT_VALUE)
                {
                    Node segmentNext = segmentNode.next();
                    if (segmentNext.sentinel())
                    {
                        break next;
                    }

                    segment.release();

                    KafkaCacheSegment newSegment;
                    do
                    {
                        newSegment = segmentNext.segment().acquire();
                        if (newSegment == null)
                        {
                            segmentNext = segmentNext.next();
                        }
                    } while (newSegment == null);

                    this.segmentNode = segmentNext;
                    this.segment = newSegment;

                    assert segmentNode != null;
                    assert !segmentNode.sentinel();
                    assert segment != null;

                    final int position = condition.reset(segment, offset, latestOffset, POSITION_UNSET);
                    this.position = position == RETRY_SEGMENT_VALUE || position == NEXT_SEGMENT_VALUE ? 0 : position;
                    continue;
                }

                final int position = positionNext;
                assert position >= 0;

                assert segment != null;
                final KafkaCacheFile logFile = segment.logFile();
                assert logFile != null;

                nextEntry = logFile.readBytes(position, cacheEntry::tryWrap);

                if (nextEntry == null)
                {
                    break next;
                }

                final long nextOffset = nextEntry.offset$();

                filters = condition.test(nextEntry);

                // TODO: when doing reset, condition.reset(condition)
                // TODO: remove nextOffset < offset from if condition
                if (nextOffset < offset || filters == 0L)
                {
                    nextEntry = null;
                }

                if (nextEntry != null && deltaType != KafkaDeltaType.NONE)
                {
                    nextEntry = markAncestorIfNecessary(cacheEntry, nextEntry);
                }

                if (nextEntry == null)
                {
                    this.offset = Math.max(offset, nextOffset);
                    this.position = positionNext + 1;
                }
                else
                {
                    this.position = positionNext;
                }
            }

            return nextEntry;
        }

        private KafkaCacheEntryFW markAncestorIfNecessary(
            KafkaCacheEntryFW cacheEntry,
            KafkaCacheEntryFW nextEntry)
        {
            final long ancestorOffset = nextEntry.ancestor();

            if (nextEntry.valueLen() == -1)
            {
                deltaKeyOffsets.remove(ancestorOffset);
            }
            else
            {
                final long partitionOffset = nextEntry.offset$();
                final int deltaPosition = nextEntry.deltaPosition();

                if (ancestorOffset != -1)
                {
                    if (deltaPosition != -1 && deltaKeyOffsets.remove(ancestorOffset))
                    {
                        final KafkaCacheFile deltaFile = segment.deltaFile();
                        final KafkaCacheDeltaFW delta = deltaFile.readBytes(deltaPosition, deltaRO::wrap);
                        final DirectBuffer entryBuffer = nextEntry.buffer();
                        final KafkaKeyFW key = nextEntry.key();
                        final int entryOffset = nextEntry.offset();
                        final ArrayFW<KafkaHeaderFW> headers = nextEntry.headers();
                        final ArrayFW<KafkaHeaderFW> trailers = nextEntry.trailers();

                        final int sizeofEntryHeader = key.limit() - nextEntry.offset();

                        int writeLimit = 0;
                        writeBuffer.putBytes(writeLimit, entryBuffer, entryOffset, sizeofEntryHeader);
                        writeLimit += sizeofEntryHeader;
                        writeBuffer.putBytes(writeLimit, delta.buffer(), delta.offset(), delta.sizeof());
                        writeLimit += delta.sizeof();
                        writeBuffer.putBytes(writeLimit, headers.buffer(), headers.offset(), headers.sizeof());
                        writeLimit += headers.sizeof();
                        writeBuffer.putBytes(writeLimit, trailers.buffer(), trailers.offset(), trailers.sizeof());
                        writeLimit += trailers.sizeof();
                        writeBuffer.putInt(writeLimit, 0);
                        writeLimit += Integer.BYTES;

                        nextEntry = cacheEntry.wrap(writeBuffer, 0, writeLimit);
                    }
                    else
                    {
                        // TODO: consider moving message to next segmentNode if delta exceeds size limit instead
                        //       still need to handle implicit snapshot case
                        writeBuffer.putBytes(0, nextEntry.buffer(), nextEntry.offset(), nextEntry.sizeof());
                        writeBuffer.putLong(KafkaCacheEntryFW.FIELD_OFFSET_ANCESTOR, -1L);
                        nextEntry = cacheEntry.wrap(writeBuffer, 0, writeBuffer.capacity());
                    }
                }

                deltaKeyOffsets.add(partitionOffset);
            }
            return nextEntry;
        }

        public void advance(
            long offset)
        {
            assert offset > this.offset : String.format("%d > %d %s", offset, this.offset, segment);
            this.offset = offset;
            this.position++;

            assert segmentNode != null;
            assert segment != null;

            KafkaCacheSegment newSegment = segmentNode.segment();
            if (segment != newSegment)
            {
                segment.release();

                Node newSegmentNode = segmentNode;
                newSegment = newSegment.acquire();
                while (newSegment == null)
                {
                    newSegment = newSegmentNode.segment().acquire();
                    if (newSegment == null)
                    {
                        newSegmentNode = newSegmentNode.next();
                    }
                }
                this.segmentNode = newSegmentNode;
                this.segment = newSegment;

                assert segmentNode != null;
                assert !segmentNode.sentinel();
                assert segment != null;

                final int position = condition.reset(segment, offset, latestOffset, POSITION_UNSET);
                this.position = position == RETRY_SEGMENT_VALUE || position == NEXT_SEGMENT_VALUE ? 0 : position;
            }
        }

        public void markEntryDirty(
            KafkaCacheEntryFW entry)
        {
            segmentNode.markDirty(entry);
        }

        @Override
        public void close()
        {
            if (segmentNode != null)
            {
                segment.release();
                segmentNode = null;
                segment = null;
            }
        }

        @Override
        public String toString()
        {
            return String.format("%s[offset %d, position %016x, segmentNode %s, condition %s]",
                    getClass().getSimpleName(), offset, position, segmentNode, condition);
        }
    }

    public abstract static class KafkaFilterCondition
    {
        public abstract int reset(
            KafkaCacheSegment segment,
            long offset,
            long latestOffset,
            int position);

        public abstract int next(
            int position);

        public abstract long test(
            KafkaCacheEntryFW cacheEntry);

        private static final class None extends KafkaFilterCondition
        {
            private KafkaCacheIndexFile indexFile;
            private long cursor;

            @Override
            public int reset(
                KafkaCacheSegment segment,
                long offset,
                long latestOffset,
                int position)
            {
                assert position == POSITION_UNSET;

                int positionNext = NEXT_SEGMENT_VALUE;

                if (segment != null)
                {
                    final KafkaCacheIndexFile indexFile = segment.indexFile();
                    assert indexFile != null;

                    this.indexFile = indexFile;

                    final int offsetDelta = (int)(offset - segment.baseOffset());
                    this.cursor = indexFile.floor(offsetDelta);
                    positionNext = cursorValue(cursor);
                }
                else
                {
                    this.indexFile = null;
                }

                return positionNext;
            }

            @Override
            public int next(
                int position)
            {
                int positionNext = NEXT_SEGMENT_VALUE;
                if (indexFile != null)
                {
                    if (position > cursorValue(cursor))
                    {
                        this.cursor = indexFile.resolve(cursor(cursorIndex(cursor), position));
                    }

                    positionNext = cursorValue(cursor);
                }
                return positionNext;
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                return cacheEntry != null ? -1L : 0L;
            }

            @Override
            public String toString()
            {
                return String.format("%s[%08x %08x]", getClass().getSimpleName(),
                        cursorValue(cursor), cursorIndex(cursor));
            }
        }

        private abstract static class Equals extends KafkaFilterCondition
        {
            private final long mask;
            private final int hash;
            private final DirectBuffer value;
            private final DirectBuffer comparable;

            private KafkaCacheIndexFile hashFile;
            private long cursor;

            @Override
            public final int reset(
                KafkaCacheSegment segment,
                long offset,
                long latestOffset,
                int position)
            {
                int positionNext = NEXT_SEGMENT_VALUE;

                if (segment != null)
                {
                    final KafkaCacheIndexFile hashFile = segment.hashFile();
                    assert hashFile != null;

                    this.hashFile = hashFile;

                    if (position == POSITION_UNSET)
                    {
                        final KafkaCacheIndexFile indexFile = segment.indexFile();
                        assert indexFile != null;
                        final int offsetDelta = (int)(offset - segment.baseOffset());
                        position = cursorValue(indexFile.floor(offsetDelta));
                    }

                    this.cursor = hashFile.first(hash);

                    if (cursorValue(cursor) != RETRY_SEGMENT_VALUE)
                    {
                        final int cursorIndex = cursorIndex(cursor);
                        final long cursorFirstHashWithPosition = cursor(cursorIndex, position);
                        this.cursor = hashFile.ceiling(hash, cursorFirstHashWithPosition);
                    }

                    final int cursorValue = cursorValue(cursor);
                    positionNext = !cursorRetryValue(cursorValue) ? cursorValue : position;
                }
                else
                {
                    this.hashFile = null;
                }

                return positionNext;
            }

            @Override
            public final int next(
                int position)
            {
                int positionNext = NEXT_SEGMENT_VALUE;
                if (hashFile != null)
                {
                    if (position > cursorValue(cursor))
                    {
                        this.cursor = hashFile.ceiling(hash, cursor(cursorIndex(cursor), position));
                    }

                    positionNext = cursorValue(cursor);
                }
                return positionNext;
            }

            @Override
            public final String toString()
            {
                return String.format("%s[%08x %08x %08x]", getClass().getSimpleName(), hash,
                        cursorValue(cursor), cursorIndex(cursor));
            }

            protected Equals(
                long mask,
                CRC32C checksum,
                DirectBuffer shared)
            {
                this.mask = mask;
                this.value = shared;
                this.hash = computeHash(shared, 0, shared.capacity(), checksum);
                this.comparable = new UnsafeBuffer();
            }

            protected Equals(
                long mask,
                CRC32C checksum,
                DirectBuffer buffer,
                int index,
                int length)
            {
                this.mask = mask;
                this.value = copyBuffer(buffer, index, length);
                this.hash = computeHash(buffer, index, length, checksum);
                this.comparable = new UnsafeBuffer();
            }

            protected final long test(
                Flyweight header)
            {
                comparable.wrap(header.buffer(), header.offset(), header.sizeof());
                return comparable.compareTo(value) == 0 ? mask : 0L;
            }
        }

        private static final class Not extends KafkaFilterCondition
        {
            private final long mask;
            private final None none;
            private final KafkaFilterCondition nested;

            private int positionSkip;

            private Not(
                long mask,
                KafkaFilterCondition nested)
            {
                this.mask = mask;
                this.none = new None();
                this.nested = nested;
            }

            @Override
            public int reset(
                KafkaCacheSegment segment,
                long offset,
                long latestOffset,
                int position)
            {
                int positionNext = none.reset(segment, offset, latestOffset, POSITION_UNSET);

                positionSkip = nested.reset(segment, offset, latestOffset, position);

                return positionNext;
            }

            @Override
            public int next(
                int position)
            {
                int positionNext = none.next(position);

                if (positionSkip == RETRY_SEGMENT_VALUE)
                {
                    positionSkip = nested.next(position);
                }

                while (positionNext != RETRY_SEGMENT_VALUE &&
                    positionSkip != NEXT_SEGMENT_VALUE &&
                    positionSkip != RETRY_SEGMENT_VALUE &&
                    positionNext > positionSkip)
                {
                    positionSkip = nested.next(positionSkip + 1);
                }

                return positionNext;
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                return (none.test(cacheEntry) == -1L &&
                    (cacheEntry.offset() < positionSkip || nested.test(cacheEntry) == 0L)) ? mask : 0L;
            }

            @Override
            public String toString()
            {
                return String.format("%s[%s]", getClass().getSimpleName(), nested.toString());
            }
        }

        private static final class HeaderSequence extends KafkaFilterCondition
        {
            private final OctetsFW name;
            private final Array32FW<KafkaValueMatchFW> matches;
            private final And and;
            private final KafkaValueMatchFW valueMatchRO;
            private final KafkaHeaderFW headersItemRO;

            private HeaderSequence(
                long mask,
                CRC32C checksum,
                KafkaValueMatchFW valueMatch,
                KafkaHeaderFW headersItem,
                KafkaHeadersFW headers)
            {
                final DirectBuffer headersCopyBuf = copyBuffer(headers.buffer(), headers.offset(), headers.limit());
                final KafkaHeadersFW headersCopy = new KafkaHeadersFW().wrap(headersCopyBuf, 0, headersCopyBuf.capacity());

                final OctetsFW name = headers.name();
                final Array32FW<KafkaValueMatchFW> matches = headers.values();
                final List<KafkaFilterCondition> conditions = new ArrayList<>();

                DirectBuffer matchItems = matches.items();
                int matchItemOffset = 0;
                int matchItemsCapacity = matchItems.capacity();

                while (matchItemOffset < matchItemsCapacity)
                {
                    final KafkaValueMatchFW matchItem = valueMatch.wrap(matchItems, matchItemOffset, matchItemsCapacity);

                    if (matchItem.kind() == KIND_VALUE)
                    {
                        final KafkaValueFW match = matchItem.value();
                        final OctetsFW value = match.value();

                        final MutableDirectBuffer headerCopyBuf =
                                new UnsafeBuffer(ByteBuffer.allocate(name.sizeof() + value.sizeof() + 8));

                        final KafkaHeaderFW headerCopy = new KafkaHeaderFW.Builder()
                                .wrap(headerCopyBuf, 0, headerCopyBuf.capacity())
                                .nameLen(name.sizeof())
                                .name(name.buffer(), name.offset(), name.sizeof())
                                .valueLen(value.sizeof())
                                .value(value.buffer(), value.offset(), value.sizeof())
                                .build();

                        conditions.add(new Header(mask, checksum, headerCopy));
                    }

                    matchItemOffset = matchItem.limit();
                }

                this.name = headersCopy.name();
                this.matches = headersCopy.values();
                this.and = new And(mask, conditions);
                this.valueMatchRO = valueMatch;
                this.headersItemRO = headersItem;
            }

            @Override
            public int reset(
                KafkaCacheSegment segment,
                long offset,
                long latestOffset,
                int position)
            {
                return and.reset(segment, offset, latestOffset, position);
            }

            @Override
            public int next(
                int position)
            {
                return and.next(position);
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                final Array32FW<KafkaHeaderFW> headers = cacheEntry.headers();
                final DirectBuffer headersItems = headers.items();
                final DirectBuffer matchItems = matches.items();
                final int matchesLimit = matchItems.capacity();

                int matchOffset = 0;
                boolean matchCandidate = true;
                boolean skipManyHeaders = false;

                int headerOffset = 0;
                int headersLimit = headersItems.capacity();

                while (matchCandidate && headerOffset < headersLimit)
                {
                    final KafkaHeaderFW header = headersItemRO.wrap(headersItems, headerOffset, headersLimit);
                    headerOffset = header.limit();

                    if (header.name().equals(name))
                    {
                        if (matchOffset < matchesLimit)
                        {
                            final KafkaValueMatchFW valueMatch = valueMatchRO.wrap(matchItems, matchOffset, matchesLimit);
                            matchOffset = valueMatch.limit();

                            switch (valueMatch.kind())
                            {
                            case KIND_VALUE:
                                final KafkaValueFW value = valueMatch.value();
                                matchCandidate &= header.value().equals(value.value());
                                break;
                            case KIND_SKIP:
                                skipManyHeaders = valueMatch.skip().get() == SKIP_MANY;
                                assert !skipManyHeaders || matchOffset == matchesLimit;
                                break;
                            }

                        }
                        else
                        {
                            matchCandidate &= skipManyHeaders;
                        }
                    }
                }

                return (matchCandidate && matchOffset == matchesLimit) ? and.mask : 0L;
            }
        }

        private static final class Key extends Equals
        {
            private static final KafkaKeyFW NULL_KEY_RO = initNullKeyRO();

            private Key(
                long mask,
                CRC32C checksum)
            {
                super(mask, checksum, NULL_KEY_RO.buffer());
            }

            private Key(
                long mask,
                CRC32C checksum,
                KafkaKeyFW key)
            {
                super(mask, checksum, key.buffer(), key.offset(), key.sizeof());
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                return test(cacheEntry.key());
            }
        }

        private static final class Header extends Equals
        {
            private final MutableLong match;

            private Header(
                long mask,
                CRC32C checksum,
                KafkaHeaderFW header)
            {
                super(mask, checksum, header.buffer(), header.offset(), header.sizeof());
                this.match = new MutableLong();
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                final ArrayFW<KafkaHeaderFW> headers = cacheEntry.headers();
                match.value = 0L;
                headers.forEach(header -> match.value |= test(header));
                return match.value;
            }
        }

        private static final class And extends KafkaFilterCondition
        {
            private final long mask;
            private final List<KafkaFilterCondition> conditions;

            private And(
                long mask,
                List<KafkaFilterCondition> conditions)
            {
                this.mask = mask;
                this.conditions = conditions;
            }

            @Override
            public int reset(
                KafkaCacheSegment segment,
                long offset,
                long latestOffset,
                int position)
            {
                int nextPositionMin = NEXT_SEGMENT_VALUE;

                if (segment != null)
                {
                    if (position == POSITION_UNSET)
                    {
                        final KafkaCacheIndexFile indexFile = segment.indexFile();
                        assert indexFile != null;
                        final int offsetDelta = (int)(offset - segment.baseOffset());
                        position = cursorValue(indexFile.floor(offsetDelta));
                    }

                    int nextPositionMax = 0;

                    for (int i = 0; i < conditions.size(); i++)
                    {
                        final KafkaFilterCondition condition = conditions.get(i);
                        final int nextPosition = condition.reset(segment, offset, latestOffset, position);

                        if (i == 0 || nextPositionMin != NEXT_SEGMENT_VALUE)
                        {
                            nextPositionMin = Math.min(nextPosition, nextPositionMin);
                            nextPositionMax = Math.max(nextPosition, nextPositionMax);
                        }
                    }

                    if (nextPositionMin == NEXT_SEGMENT_VALUE)
                    {
                        nextPositionMax = nextPositionMin;
                    }

                    if (nextPositionMax == RETRY_SEGMENT_VALUE ||
                        nextPositionMax == NEXT_SEGMENT_VALUE)
                    {
                        nextPositionMin = nextPositionMax;
                    }
                }

                return nextPositionMin;
            }

            @Override
            public int next(
                int position)
            {
                int nextPosition = RETRY_SEGMENT_VALUE;
                int nextPositionMin = position == RETRY_SEGMENT_VALUE ? -1 : position - 1;
                int nextPositionMax;

                do
                {
                    nextPositionMax = nextPositionMin + 1;
                    nextPositionMin = Integer.MAX_VALUE;

                    final int nextCursorAnd = nextPositionMax;

                    for (int i = 0; i < conditions.size(); i++)
                    {
                        final KafkaFilterCondition condition = conditions.get(i);

                        nextPosition = condition.next(nextCursorAnd);

                        nextPositionMin = Math.min(nextPosition, nextPositionMin);
                        nextPositionMax = Math.max(nextPosition, nextPositionMax);

                        if (nextPositionMin == NEXT_SEGMENT_VALUE)
                        {
                            nextPositionMax = nextPositionMin;
                            break;
                        }
                    }

                    if (nextPositionMin == RETRY_SEGMENT_VALUE)
                    {
                        nextPositionMax = nextPositionMin;
                        break;
                    }

                    if (nextPositionMax == NEXT_SEGMENT_VALUE)
                    {
                        nextPositionMin = nextPositionMax;
                        break;
                    }
                }
                while (nextPositionMin != nextPositionMax);

                return nextPositionMin;
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                long accept = mask;
                for (int i = 0; accept != 0L && i < conditions.size(); i++)
                {
                    final KafkaFilterCondition condition = conditions.get(i);
                    accept &= condition.test(cacheEntry);
                }
                return accept;
            }

            @Override
            public String toString()
            {
                return String.format("%s%s", getClass().getSimpleName(), conditions);
            }
        }

        private abstract static class Or extends KafkaFilterCondition
        {
            private final List<KafkaFilterCondition> conditions;

            Or(
                List<KafkaFilterCondition> conditions)
            {
                this.conditions = conditions;
            }

            @Override
            public int reset(
                KafkaCacheSegment segment,
                long offset,
                long latestOffset,
                int position)
            {
                int nextPositionMin = NEXT_SEGMENT_VALUE;

                if (segment != null)
                {
                    if (position == POSITION_UNSET)
                    {
                        final KafkaCacheIndexFile indexFile = segment.indexFile();
                        assert indexFile != null;
                        final int offsetDelta = (int)(offset - segment.baseOffset());
                        position = cursorValue(indexFile.floor(offsetDelta));
                    }

                    nextPositionMin = NEXT_SEGMENT_VALUE;
                    for (int i = 0; i < conditions.size(); i++)
                    {
                        final KafkaFilterCondition condition = conditions.get(i);
                        final int nextPosition = condition.reset(segment, offset, latestOffset, position);
                        nextPositionMin = Math.min(nextPosition, nextPositionMin);
                    }
                }

                return nextPositionMin;
            }

            @Override
            public int next(
                int position)
            {
                int nextPositionMin = NEXT_SEGMENT_VALUE;
                int nextPositionMax = RETRY_SEGMENT_VALUE;
                for (int i = 0; i < conditions.size(); i++)
                {
                    final KafkaFilterCondition condition = conditions.get(i);
                    final int nextPosition = condition.next(position);
                    if (nextPosition != RETRY_SEGMENT_VALUE)
                    {
                        nextPositionMin = Math.min(nextPosition, nextPositionMin);
                    }
                    nextPositionMax = Math.max(nextPosition, nextPositionMax);
                }

                if (nextPositionMax == RETRY_SEGMENT_VALUE)
                {
                    nextPositionMin = RETRY_SEGMENT_VALUE;
                }

                return nextPositionMin;
            }

            @Override
            public String toString()
            {
                return String.format("%s%s", getClass().getSimpleName(), conditions);
            }
        }

        private static final class LazyOr extends Or
        {
            private final List<KafkaFilterCondition> conditions;

            private LazyOr(
                List<KafkaFilterCondition> conditions)
            {
                super(conditions);
                this.conditions = conditions;
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                long accept = 0L;
                for (int i = 0; accept == 0L && i < conditions.size(); i++)
                {
                    final KafkaFilterCondition condition = conditions.get(i);
                    accept |= condition.test(cacheEntry);
                }
                return accept;
            }

            @Override
            public String toString()
            {
                return String.format("%s%s", getClass().getSimpleName(), conditions);
            }
        }

        private static final class EagerOr extends Or
        {
            private final List<KafkaFilterCondition> conditions;

            private EagerOr(
                List<KafkaFilterCondition> conditions)
            {
                super(conditions);
                this.conditions = conditions;
            }

            @Override
            public long test(
                KafkaCacheEntryFW cacheEntry)
            {
                long accept = 0L;
                for (int i = 0; i < conditions.size(); i++)
                {
                    final KafkaFilterCondition condition = conditions.get(i);
                    accept |= condition.test(cacheEntry);
                }
                return accept;
            }

            @Override
            public String toString()
            {
                return String.format("%s%s", getClass().getSimpleName(), conditions);
            }
        }

        private static DirectBuffer copyBuffer(
            DirectBuffer buffer,
            int index,
            int length)
        {
            UnsafeBuffer copy = new UnsafeBuffer(new byte[length]);
            copy.putBytes(0, buffer, index, length);
            return copy;
        }

        private static int computeHash(
            DirectBuffer buffer,
            int index,
            int length,
            CRC32C checksum)
        {
            final ByteBuffer byteBuffer = buffer.byteBuffer();
            assert byteBuffer != null;
            byteBuffer.clear();
            byteBuffer.position(index);
            byteBuffer.limit(index + length);
            checksum.reset();
            checksum.update(byteBuffer);
            return (int) checksum.getValue();
        }
    }

    public KafkaFilterCondition asCondition(
        ArrayFW<KafkaFilterFW> filters,
        KafkaEvaluation evaluation)
    {
        KafkaFilterCondition condition = null;
        if (filters.isEmpty())
        {
            condition = new KafkaFilterCondition.None();
        }
        else
        {
            final List<KafkaFilterCondition> asConditions = new ArrayList<>();
            MutableInteger index = new MutableInteger();
            filters.forEach(f -> asConditions.add(asCondition(1L << index.value++, f)));
            switch (evaluation)
            {
            case EAGER:
                condition = asConditions.size() == 1 ? asConditions.get(0) : new KafkaFilterCondition.EagerOr(asConditions);
                break;
            case LAZY:
                condition = asConditions.size() == 1 ? asConditions.get(0) : new KafkaFilterCondition.LazyOr(asConditions);
                break;
            }
        }
        return condition;
    }

    private KafkaFilterCondition asCondition(
        long mask,
        KafkaFilterFW filter)
    {
        final ArrayFW<KafkaConditionFW> conditions = filter.conditions();
        assert !conditions.isEmpty();
        List<KafkaFilterCondition> asConditions = new ArrayList<>();
        conditions.forEach(c -> asConditions.add(asCondition(mask, c)));
        return asConditions.size() == 1 ? asConditions.get(0) : new KafkaFilterCondition.And(mask, asConditions);
    }

    private KafkaFilterCondition asCondition(
        long mask,
        KafkaConditionFW condition)
    {
        KafkaFilterCondition asCondition = null;

        switch (condition.kind())
        {
        case KafkaConditionFW.KIND_KEY:
            asCondition = asKeyCondition(mask, condition.key());
            break;
        case KafkaConditionFW.KIND_HEADER:
            asCondition = asHeaderCondition(mask, condition.header());
            break;
        case KafkaConditionFW.KIND_NOT:
            asCondition = asNotCondition(mask, condition.not());
            break;
        case KafkaConditionFW.KIND_HEADERS:
            asCondition = asHeadersCondition(mask, condition.headers());
            break;
        }

        assert asCondition != null;
        return asCondition;
    }

    private KafkaFilterCondition asKeyCondition(
        long mask,
        KafkaKeyFW key)
    {
        final OctetsFW value = key.value();

        return value == null ?
            new KafkaFilterCondition.Key(mask, checksum) : new KafkaFilterCondition.Key(mask, checksum, key);

    }

    private KafkaFilterCondition asHeaderCondition(
        long mask,
        KafkaHeaderFW header)
    {
        return new KafkaFilterCondition.Header(mask, checksum, header);
    }

    private KafkaFilterCondition asNotCondition(
        long mask,
        KafkaNotFW not)
    {
        final KafkaConditionFW condition = not.condition();

        KafkaFilterCondition filterCondition = null;
        switch (condition.kind())
        {
        case KafkaConditionFW.KIND_KEY:
            filterCondition = new KafkaFilterCondition.Not(mask, asKeyCondition(mask, condition.key()));
            break;
        case KafkaConditionFW.KIND_HEADER:
            filterCondition = new KafkaFilterCondition.Not(mask, asHeaderCondition(mask, condition.header()));
            break;
        case KafkaConditionFW.KIND_NOT:
            filterCondition = asCondition(mask, condition.not().condition());
            break;
        case KafkaConditionFW.KIND_HEADERS:
            filterCondition = new KafkaFilterCondition.Not(mask, asHeadersCondition(mask, condition.headers()));
            break;
        }
        return filterCondition;
    }

    private KafkaFilterCondition asHeadersCondition(
        long mask,
        KafkaHeadersFW headers)
    {
        return new KafkaFilterCondition.HeaderSequence(mask, checksum, valueMatchRO, headerRO, headers);
    }

    private static KafkaKeyFW initNullKeyRO()
    {
        final KafkaKeyFW nullKeyRO = new KafkaKeyFW.Builder()
                .wrap(new UnsafeBuffer(ByteBuffer.allocate(5)), 0, 5)
                .length(-1)
                .value((OctetsFW) null)
                .build();

        return nullKeyRO;
    }
}
