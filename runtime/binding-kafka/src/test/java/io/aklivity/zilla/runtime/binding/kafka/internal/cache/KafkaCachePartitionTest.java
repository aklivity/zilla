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

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_BUFFER_SLOT_CAPACITY;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.agrona.collections.MutableInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.config.engine.test.internal.model.config.TestModelConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTimestampType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCachePaddedKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCachePaddedValueFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelFieldBridge;
import io.aklivity.zilla.runtime.engine.model.ModelHandler;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelHandler;

public class KafkaCachePartitionTest
{
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final MutableDirectBufferEx scratch = new UnsafeBufferEx(new byte[8192]);
    private final KafkaCacheEntryFW entryRO = new KafkaCacheEntryFW();
    private final KafkaCachePaddedValueFW paddedValueRO = new KafkaCachePaddedValueFW();

    @Test
    public void shouldSeekNotAfterNotFound() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(partition.sentinel(), partition.seekNotAfter(5));
    }

    @Test
    public void shouldSeekNotAfterEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(10, partition.seekNotAfter(10).segment().baseOffset());
    }

    @Test
    public void shouldSeekNotAfterNotEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(10, partition.seekNotAfter(15).segment().baseOffset());
    }

    @Test
    public void shouldSeekNotBeforeNotFound() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(partition.sentinel(), partition.seekNotBefore(35));
    }

    @Test
    public void shouldSeekNotBeforeEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(10, partition.seekNotBefore(10).segment().baseOffset());
    }

    @Test
    public void shouldSeekNotBeforeNotEquals() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        assertEquals(20, partition.seekNotBefore(15).segment().baseOffset());
    }

    @Test
    public void shouldReplaceSegment() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        Node node10 = partition.append(10);
        KafkaCacheSegment node10s = node10.segment();

        Node node20 = partition.append(20);
        KafkaCacheSegment node20s = node20.segment();

        Node node30 = partition.append(30);
        KafkaCacheSegment node30s = node30.segment();

        assertNotSame(node10s, node10.segment());
        assertNotSame(node20s, node20.segment());
        assertSame(node30s, node30.segment());
    }

    @Test
    public void shouldRemoveSegment() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        partition.append(10);
        partition.append(20);
        partition.append(30);

        Node sentinel = partition.sentinel();
        Node node10 = sentinel.next();
        Node node20 = node10.next();
        Node node30 = node20.next();

        node20.remove();

        assertSame(node10, node30.previous());
        assertSame(node30, node10.next());
    }

    @Test
    public void shouldDescribeObject() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        assertEquals("cache", partition.cache());
        assertEquals("test", partition.topic());
        assertEquals(0, partition.id());
        assertEquals("[cache] test[0]", partition.toString());
    }

    @Test
    public void shouldRoundTripPaddedValueWithHeadersAndTrailersIntact() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
            new KafkaCacheKeyEnvelope(), new KafkaCacheTrailerEnvelope(), 256, false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);

        assertEquals(5, entry.paddedValue().length());
        assertArrayEquals("hello".getBytes(UTF_8), bytes(entry.paddedValue().value()));
        assertEquals(0, entry.headers().fieldCount());
        assertEquals(0, entry.trailers().fieldCount());
    }

    @Test
    public void shouldRoundTripNullPaddedValue() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, null, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
            new KafkaCacheKeyEnvelope(), new KafkaCacheTrailerEnvelope(), 256, false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);

        assertEquals(-1, entry.paddedValue().length());
    }

    @Test
    public void shouldNotInflateHashReservationFromValuePaddingMax() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        config.segmentIndexBytes = 256;
        KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);

        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[64]);
        KafkaKeyFW key = key(buffer, "k");

        Node first = partition.newHeadIfNecessary(10L, key, 10, 0, 0);
        Node second = partition.newHeadIfNecessary(11L, key, 10, 1_000_000, 0);

        assertSame(first, second);
    }

    @Test
    public void shouldStreamTransformedValueDirectlyIntoLogFileForProduce() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger trailersClaimMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        UppercasingPipeline pipeline = new UppercasingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);

        int transformed = partition.writeProduceEntryStart(1L, 1L, 0L, 11L, head, entryMark, valueMark, valueLimit,
            trailersClaimMark, 0L, 1L, -1L, (short) 0, 0, KafkaAckMode.NONE, key, 5, 4, headers, 256,
            value, KafkaCacheModel.NONE, transformValue);
        assertNotEquals(-1, transformed);

        int continued = partition.writeProduceEntryContinue(1L, 1L, 0L, 0x03, head, entryMark, valueMark, valueLimit,
            value, transformValue, 4);
        assertNotEquals(-1, continued);

        partition.writeProduceEntryFin(head, entryMark, valueLimit, 0L, noHeaders(buffer, 0), false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(7, entry.paddedValue().length());
        assertArrayEquals("HELLO!!".getBytes(UTF_8), bytes(entry.paddedValue().value()));
        assertEquals(1, pipeline.resetCount);

        assertFalse(containsBytes(head.segment().logFile().buffer(), entryMark.value, entry.limit() - entryMark.value,
            "hello".getBytes(UTF_8)));
    }

    @Test
    public void shouldStreamTransformedValueAcrossMultipleFragmentsForProduce() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger trailersClaimMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW firstFragment = value(buffer, headers.limit(), "hel");
        OctetsFW secondFragment = value(buffer, headers.limit() + 3, "lo");

        UppercasingPipeline pipeline = new UppercasingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);

        int transformed = partition.writeProduceEntryStart(1L, 1L, 0L, 11L, head, entryMark, valueMark, valueLimit,
            trailersClaimMark, 0L, 1L, -1L, (short) 0, 0, KafkaAckMode.NONE, key, 5, 4, headers, 256,
            firstFragment, KafkaCacheModel.NONE, transformValue);
        assertNotEquals(-1, transformed);

        assertNotEquals(-1, partition.writeProduceEntryContinue(1L, 1L, 0L, 0x02, head, entryMark, valueMark, valueLimit,
            firstFragment, transformValue, 4));
        assertNotEquals(-1, partition.writeProduceEntryContinue(1L, 1L, 0L, 0x01, head, entryMark, valueMark, valueLimit,
            secondFragment, transformValue, 4));

        partition.writeProduceEntryFin(head, entryMark, valueLimit, 0L, noHeaders(buffer, 0), false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(7, entry.paddedValue().length());
        assertArrayEquals("HELLO!!".getBytes(UTF_8), bytes(entry.paddedValue().value()));
    }

    @Test
    public void shouldAbortProduceEntryWhenTransformedValueExceedsReservation() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger trailersClaimMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        UppercasingPipeline pipeline = new UppercasingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);

        partition.writeProduceEntryStart(1L, 1L, 0L, 11L, head, entryMark, valueMark, valueLimit,
            trailersClaimMark, 0L, 1L, -1L, (short) 0, 0, KafkaAckMode.NONE, key, 5, 0, headers, 256,
            value, KafkaCacheModel.NONE, transformValue);

        partition.writeProduceEntryContinue(1L, 1L, 0L, 0x03, head, entryMark, valueMark, valueLimit,
            value, transformValue, 0);

        int flags = head.segment().logFile().readInt(entryMark.value + KafkaCacheEntryFW.FIELD_OFFSET_FLAGS);
        assertEquals(KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED,
            flags & KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED);
    }

    @Test
    public void shouldPersistValuePaddingMaxAcrossInitEmptyAndFinEmptyFragmentsForProduce() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger trailersClaimMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW emptyValue = value(buffer, headers.limit(), "");
        OctetsFW payload = value(buffer, headers.limit(), "hello");

        DoublingPipeline pipeline = new DoublingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);

        final int valuePaddingMax = 8;
        partition.writeProduceEntryStart(1L, 1L, 0L, 11L, head, entryMark, valueMark, valueLimit,
            trailersClaimMark, 0L, 1L, -1L, (short) 0, 0, KafkaAckMode.NONE, key, 5, valuePaddingMax, headers, 256,
            emptyValue, KafkaCacheModel.NONE, transformValue);

        // the real HTTP-to-Kafka produce path splits a value across an INIT-flagged fragment with an
        // empty payload, one or more fragments carrying the real bytes, and a FIN-flagged fragment with
        // an empty payload -- reproduce that split here
        assertNotEquals(-1, partition.writeProduceEntryContinue(1L, 1L, 0L, 0x02, head, entryMark, valueMark, valueLimit,
            emptyValue, transformValue, valuePaddingMax));

        // this fragment's doubled output (10 bytes) writes past the 5-byte raw-value reservation, into
        // the on-disk valuePaddingMax marker's own position -- corrupting it if a later fragment
        // re-derived valuePaddingMax from disk instead of being handed it directly
        assertNotEquals(-1, partition.writeProduceEntryContinue(1L, 1L, 0L, 0x00, head, entryMark, valueMark, valueLimit,
            payload, transformValue, valuePaddingMax));

        assertNotEquals(-1, partition.writeProduceEntryContinue(1L, 1L, 0L, 0x01, head, entryMark, valueMark, valueLimit,
            emptyValue, transformValue, valuePaddingMax));

        partition.writeProduceEntryFin(head, entryMark, valueLimit, 0L, noHeaders(buffer, 0), false);

        final int actualLength = 10;
        final int finalPaddingLenAt = valueMark.value + actualLength;
        final int finalPaddingLen = head.segment().logFile().readInt(finalPaddingLenAt);
        assertEquals(5 + valuePaddingMax - actualLength, finalPaddingLen);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(actualLength, entry.paddedValue().length());
        assertArrayEquals("hheelllloo".getBytes(UTF_8), bytes(entry.paddedValue().value()));
    }

    @Test
    public void shouldPersistValuePaddingMaxAcrossInitEmptyAndFinEmptyFragmentsForPopulate() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger headersMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW emptyValue = value(buffer, headers.limit(), "");
        OctetsFW payload = value(buffer, headers.limit(), "hello");

        DoublingPipeline pipeline = new DoublingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);
        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();

        final int valuePaddingMax = 8;
        partition.writeEntryStart(null, 1L, 1L, 0L, 11L, entryMark, valueMark, valueLimit, headersMark, 0L,
            KafkaTimestampType.ADVISORY, -1L, key, 5, valuePaddingMax, headers.sizeof(), 256, null, 0x00,
            KafkaDeltaType.NONE, emptyValue, KafkaCacheModel.NONE, transformValue, new KafkaCacheKeyEnvelope(),
            valueEnvelope, false);

        assertNotEquals(-1, partition.writeEntryContinue(1L, 1L, 0L, 0x02, entryMark, valueMark, valueLimit,
            emptyValue, transformValue, valuePaddingMax));
        assertNotEquals(-1, partition.writeEntryContinue(1L, 1L, 0L, 0x00, entryMark, valueMark, valueLimit,
            payload, transformValue, valuePaddingMax));
        assertNotEquals(-1, partition.writeEntryContinue(1L, 1L, 0L, 0x01, entryMark, valueMark, valueLimit,
            emptyValue, transformValue, valuePaddingMax));

        partition.writeEntryFinish(headers, KafkaDeltaType.NONE, entryMark, valueMark, headersMark, headers.sizeof(),
            transformValue, valueEnvelope, 256);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(10, entry.paddedValue().length());
        assertArrayEquals("hheelllloo".getBytes(UTF_8), bytes(entry.paddedValue().value()));
        assertEquals(0, entry.headers().fieldCount());
        assertEquals(0, entry.trailers().fieldCount());
    }

    private static boolean containsBytes(
        DirectBufferEx haystack,
        int offset,
        int length,
        byte[] needle)
    {
        outer:
        for (int i = 0; i <= length - needle.length; i++)
        {
            for (int j = 0; j < needle.length; j++)
            {
                if (haystack.getByte(offset + i + j) != needle[j])
                {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    // uppercases ASCII input and appends "!!" once complete, so a test can distinguish transformed
    // (longer, uppercase) output from the raw bytes it must never leave behind in logFile
    private static final class UppercasingPipeline implements ModelPipeline
    {
        private static final int FLAGS_FIN = 0x01;

        private final ModelPipelineResult result = new ModelPipelineResult();
        private int resetCount;

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int length = srcLimit - srcIndex;
            for (int i = 0; i < length; i++)
            {
                byte value = src.getByte(srcIndex + i);
                if (value >= 'a' && value <= 'z')
                {
                    value -= 32;
                }
                dst.putByte(dstIndex + i, value);
            }

            int produced = length;
            final boolean fin = (flags & FLAGS_FIN) != 0;
            if (fin)
            {
                dst.putByte(dstIndex + length, (byte) '!');
                dst.putByte(dstIndex + length + 1, (byte) '!');
                produced += 2;
            }

            final ModelStatus status = fin ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;
            return result.set(status, length, produced);
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public void reset()
        {
            resetCount++;
        }
    }

    // doubles every input byte, so a fragment's output can exceed the raw-value reservation entirely
    // within that one fragment -- a test can then split an empty INIT fragment, this fragment, and an
    // empty FIN fragment across three separate calls to prove valuePaddingMax survives the split
    private static final class DoublingPipeline implements ModelPipeline
    {
        private static final int FLAGS_FIN = 0x01;

        private final ModelPipelineResult result = new ModelPipelineResult();

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int length = srcLimit - srcIndex;
            for (int i = 0; i < length; i++)
            {
                byte value = src.getByte(srcIndex + i);
                dst.putByte(dstIndex + i * 2, value);
                dst.putByte(dstIndex + i * 2 + 1, value);
            }

            final int produced = length * 2;
            final boolean fin = (flags & FLAGS_FIN) != 0;
            final ModelStatus status = fin ? ModelStatus.COMPLETE : ModelStatus.UNDERFLOW;
            return result.set(status, length, produced);
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public void reset()
        {
        }
    }

    @Test
    public void shouldTransformValueWithDecodeModel() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "abc");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        KafkaCacheModel transformValue = KafkaCacheModel.writer(handler(5), ModelTransform.NONE, valueEnvelope, scratch);

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(5, entry.paddedValue().length());
        assertArrayEquals("hello".getBytes(UTF_8), bytes(entry.paddedValue().value()));
    }

    @Test
    public void shouldStreamTransformedValueDirectlyIntoLogFileForPopulate() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger headersMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        UppercasingPipeline pipeline = new UppercasingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);
        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();

        partition.writeEntryStart(null, 1L, 1L, 0L, 11L, entryMark, valueMark, valueLimit, headersMark, 0L,
            KafkaTimestampType.ADVISORY, -1L, key, 5, 4, headers.sizeof(), 256, null, 0x00, KafkaDeltaType.NONE,
            value, KafkaCacheModel.NONE, transformValue, new KafkaCacheKeyEnvelope(), valueEnvelope, false);

        assertNotEquals(-1, partition.writeEntryContinue(1L, 1L, 0L, 0x03, entryMark, valueMark, valueLimit,
            value, transformValue, 4));

        partition.writeEntryFinish(headers, KafkaDeltaType.NONE, entryMark, valueMark, headersMark, headers.sizeof(),
            transformValue, valueEnvelope, 256);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(7, entry.paddedValue().length());
        assertArrayEquals("HELLO!!".getBytes(UTF_8), bytes(entry.paddedValue().value()));
        assertEquals(1, pipeline.resetCount);

        assertFalse(containsBytes(head.segment().logFile().buffer(), entryMark.value, entry.limit() - entryMark.value,
            "hello".getBytes(UTF_8)));
    }

    @Test
    public void shouldStreamTransformedValueAcrossMultipleFragmentsForPopulate() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger headersMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW firstFragment = value(buffer, headers.limit(), "hel");
        OctetsFW secondFragment = value(buffer, headers.limit() + 3, "lo");

        UppercasingPipeline pipeline = new UppercasingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);
        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();

        partition.writeEntryStart(null, 1L, 1L, 0L, 11L, entryMark, valueMark, valueLimit, headersMark, 0L,
            KafkaTimestampType.ADVISORY, -1L, key, 5, 4, headers.sizeof(), 256, null, 0x00, KafkaDeltaType.NONE,
            firstFragment, KafkaCacheModel.NONE, transformValue, new KafkaCacheKeyEnvelope(), valueEnvelope, false);

        assertNotEquals(-1, partition.writeEntryContinue(1L, 1L, 0L, 0x02, entryMark, valueMark, valueLimit,
            firstFragment, transformValue, 4));
        assertNotEquals(-1, partition.writeEntryContinue(1L, 1L, 0L, 0x01, entryMark, valueMark, valueLimit,
            secondFragment, transformValue, 4));

        partition.writeEntryFinish(headers, KafkaDeltaType.NONE, entryMark, valueMark, headersMark, headers.sizeof(),
            transformValue, valueEnvelope, 256);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(7, entry.paddedValue().length());
        assertArrayEquals("HELLO!!".getBytes(UTF_8), bytes(entry.paddedValue().value()));
    }

    @Test
    public void shouldAbortEntryWhenTransformedValueExceedsReservation() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger headersMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        UppercasingPipeline pipeline = new UppercasingPipeline();
        KafkaCacheModel transformValue = new KafkaCacheModel(pipeline, scratch);
        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();

        partition.writeEntryStart(null, 1L, 1L, 0L, 11L, entryMark, valueMark, valueLimit, headersMark, 0L,
            KafkaTimestampType.ADVISORY, -1L, key, 5, 0, headers.sizeof(), 256, null, 0x00, KafkaDeltaType.NONE,
            value, KafkaCacheModel.NONE, transformValue, new KafkaCacheKeyEnvelope(), valueEnvelope, false);

        partition.writeEntryContinue(1L, 1L, 0L, 0x03, entryMark, valueMark, valueLimit, value, transformValue, 0);

        partition.writeEntryFinish(headers, KafkaDeltaType.NONE, entryMark, valueMark, headersMark, headers.sizeof(),
            transformValue, valueEnvelope, 256);

        int flags = head.segment().logFile().readInt(entryMark.value + KafkaCacheEntryFW.FIELD_OFFSET_FLAGS);
        assertEquals(KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED,
            flags & KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertEquals(0, entry.headers().sizeof() - headers.sizeof());
        assertTrue(entry.trailers().isEmpty());
    }

    @Test
    public void shouldSizeValueReservationFromFullValueLengthNotFirstFragment() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableInteger valueLimit = new MutableInteger(0);
        MutableInteger trailersClaimMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW firstFragment = value(buffer, headers.limit(), "he");
        int valueLength = 5;

        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        KafkaCacheModel transformValue = KafkaCacheModel.writer(paddingHandler(), ModelTransform.NONE, valueEnvelope, scratch);

        int valuePaddingMax = transformValue.padding(firstFragment.buffer(), firstFragment.offset(), valueLength);

        partition.writeProduceEntryStart(1L, 1L, 0L, 11L, head, entryMark, valueMark, valueLimit,
            trailersClaimMark, 0L, 1L, -1L, (short) 0, 0, KafkaAckMode.NONE, key, valueLength, valuePaddingMax, headers, 256,
            firstFragment, KafkaCacheModel.NONE, transformValue);

        final int paddingLenAt = valueMark.value + valueLength;
        final int reservedPadding = head.segment().logFile().readInt(paddingLenAt);
        assertEquals(valueLength * 2, reservedPadding);
    }

    @Test
    public void shouldAbortEntryWhenValueRejected() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "abc");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        KafkaCacheModel transformValue = KafkaCacheModel.writer(handler(99), ModelTransform.NONE, valueEnvelope, scratch);

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        int flags = head.segment().logFile().readInt(entryMark.value + KafkaCacheEntryFW.FIELD_OFFSET_FLAGS);
        assertEquals(KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED,
            flags & KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED);
    }

    @Test
    public void shouldExtractKeyAndHeader() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "key1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "regionEast");

        KafkaCacheKeyEnvelope keyEnvelope = new KafkaCacheKeyEnvelope();
        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        ModelTransform keyTransform = new KafkaExtractTransform("$.key", KafkaCacheKeyEnvelope.NAME, keyEnvelope);
        ModelTransform valueTransform = new KafkaExtractTransform("$.region", "region", valueEnvelope);
        KafkaCacheModel transformKey =
            KafkaCacheModel.writer(extractingHandler("$.key"), keyTransform, keyEnvelope, scratch);
        KafkaCacheModel transformValue =
            KafkaCacheModel.writer(extractingHandler("$.region"), valueTransform, valueEnvelope, scratch);

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, transformKey, transformValue, keyEnvelope, valueEnvelope,
            256, false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);

        KafkaCachePaddedKeyFW paddedKey = entry.paddedKey();
        assertArrayEquals("key1".getBytes(UTF_8), bytes(paddedKey.key().value()));

        MutableInteger trailerCount = new MutableInteger(0);
        entry.trailers().forEach(trailer ->
        {
            trailerCount.value++;
            assertArrayEquals("region".getBytes(UTF_8), bytes(trailer.name()));
            assertArrayEquals("regionEast".getBytes(UTF_8), bytes(trailer.value()));
        });
        assertEquals(1, trailerCount.value);
    }

    @Test
    public void shouldExtractHeaderAcrossSequentialEntriesReusingSameEnvelope() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        ModelTransform valueTransform = new KafkaExtractTransform("$.region", "region", valueEnvelope);
        KafkaCacheModel transformValue =
            KafkaCacheModel.writer(extractingHandler("$.region"), valueTransform, valueEnvelope, scratch);

        KafkaKeyFW key1 = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers1 = noHeaders(buffer, key1.limit());
        OctetsFW value1 = value(buffer, headers1.limit(), "east");

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key1, headers1, value1, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        assertEquals("east", trailerValue(head, entryMark));

        KafkaKeyFW key2 = key(buffer, "k2");
        Array32FW<KafkaHeaderFW> headers2 = noHeaders(buffer, key2.limit());
        OctetsFW value2 = value(buffer, headers2.limit(), "west");

        partition.writeEntry(null, 1L, 1L, 0L, 12L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key2, headers2, value2, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        assertEquals("west", trailerValue(head, entryMark));
    }

    @Test
    public void shouldAbortEntryWhenKeyOverrideExceedsReservedPadding() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "hello");

        KafkaCacheKeyEnvelope keyEnvelope = new KafkaCacheKeyEnvelope();
        ModelTransform keyTransform = new KafkaExtractTransform("$.id", KafkaCacheKeyEnvelope.NAME, keyEnvelope);
        KafkaCacheModel transformKey = KafkaCacheModel.writer(
            fieldsHandler("$.id", "this-override-is-much-longer-than-the-original-key"), keyTransform, keyEnvelope, scratch);

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, transformKey, KafkaCacheModel.NONE, keyEnvelope,
            new KafkaCacheTrailerEnvelope(), 256, false);

        int flags = head.segment().logFile().readInt(entryMark.value + KafkaCacheEntryFW.FIELD_OFFSET_FLAGS);
        assertEquals(KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED,
            flags & KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED);
    }

    @Test
    public void shouldAbortEntryWhenExtractedHeadersOverflowClaimedBlock() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "k1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "regionEast");

        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        ModelTransform valueTransform = new KafkaExtractTransform("$.region", "region", valueEnvelope);
        KafkaCacheModel transformValue =
            KafkaCacheModel.writer(extractingHandler("$.region"), valueTransform, valueEnvelope, scratch);

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 16, false);

        int flags = head.segment().logFile().readInt(entryMark.value + KafkaCacheEntryFW.FIELD_OFFSET_FLAGS);
        assertEquals(KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED,
            flags & KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED);
    }

    private String trailerValue(
        Node head,
        MutableInteger entryMark)
    {
        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        StringBuilder trailer = new StringBuilder();
        entry.trailers().forEach(t -> trailer.append(t.value().buffer().getStringWithoutLengthUtf8(t.value().offset(),
            t.value().sizeof())));
        return trailer.toString();
    }

    @Test
    public void shouldExtractHeaderAfterNullKeyEntry() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
        ModelTransform valueTransform = new KafkaExtractTransform("$.region", "region", valueEnvelope);
        KafkaCacheModel transformValue =
            KafkaCacheModel.writer(extractingHandler("$.region"), valueTransform, valueEnvelope, scratch);

        KafkaKeyFW nullKey = new OctetsFW().wrap(new UnsafeBufferEx(new byte[] { 0x00 }), 0, 1)
            .get(new KafkaKeyFW()::wrap);
        Array32FW<KafkaHeaderFW> headers1 = noHeaders(buffer, 0);
        OctetsFW value1 = value(buffer, headers1.limit(), "east");

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            nullKey, headers1, value1, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        assertEquals("east", trailerValue(head, entryMark));

        KafkaKeyFW realKey = key(buffer, "key1");
        Array32FW<KafkaHeaderFW> headers2 = noHeaders(buffer, realKey.limit());
        OctetsFW value2 = value(buffer, headers2.limit(), "west");

        partition.writeEntry(null, 1L, 1L, 0L, 12L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            realKey, headers2, value2, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        int flags = head.segment().logFile().readInt(entryMark.value + KafkaCacheEntryFW.FIELD_OFFSET_FLAGS);
        assertEquals(0, flags & KafkaCachePartition.CACHE_ENTRY_FLAGS_ABORTED);
        assertEquals("west", trailerValue(head, entryMark));
    }

    @Test
    public void shouldExtractKeyFromOneFieldOfAStructuredKey() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaKeyFW key = key(buffer, "tenantA/id42/euw1");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), "payload");

        KafkaCacheKeyEnvelope keyEnvelope = new KafkaCacheKeyEnvelope();
        ModelTransform keyTransform = new KafkaExtractTransform("$.id", KafkaCacheKeyEnvelope.NAME, keyEnvelope);

        // the key's model surfaces the extracted field between two others, so the entry key is only right
        // if the fields the traversal merely surfaces stay out of the key lane
        KafkaCacheModel transformKey = KafkaCacheModel.writer(
            fieldsHandler("$.tenant", "tenantA", "$.id", "id42", "$.zone", "euw1"), keyTransform, keyEnvelope, scratch);

        partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, transformKey, KafkaCacheModel.NONE, keyEnvelope,
            new KafkaCacheTrailerEnvelope(), 256, false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);

        assertArrayEquals("id42".getBytes(UTF_8), bytes(entry.paddedKey().key().value()));
    }

    @Test
    public void shouldIsolateInterleavedStreams() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaCacheTrailerEnvelope envelopeA = new KafkaCacheTrailerEnvelope();
        KafkaCacheTrailerEnvelope envelopeB = new KafkaCacheTrailerEnvelope();
        KafkaCacheModel transformValueA = KafkaCacheModel.writer(extractingHandler("$.region"),
            new KafkaExtractTransform("$.region", "region", envelopeA), envelopeA, scratch);
        KafkaCacheModel transformValueB = KafkaCacheModel.writer(extractingHandler("$.region"),
            new KafkaExtractTransform("$.region", "region", envelopeB), envelopeB, scratch);

        assertEquals("AAA", writeAndReadTrailer(partition, head, entryMark, valueMark, buffer, 11L, "AAA",
            transformValueA, envelopeA));
        assertEquals("BBBB", writeAndReadTrailer(partition, head, entryMark, valueMark, buffer, 12L, "BBBB",
            transformValueB, envelopeB));
        assertEquals("CC", writeAndReadTrailer(partition, head, entryMark, valueMark, buffer, 13L, "CC",
            transformValueA, envelopeA));
    }

    private String writeAndReadTrailer(
        KafkaCachePartition partition,
        Node head,
        MutableInteger entryMark,
        MutableInteger valueMark,
        MutableDirectBufferEx buffer,
        long offset,
        String valueText,
        KafkaCacheModel transformValue,
        KafkaCacheTrailerEnvelope valueEnvelope)
    {
        KafkaKeyFW key = key(buffer, "k");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), valueText);

        partition.writeEntry(null, 1L, 1L, 0L, offset, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue,
            new KafkaCacheKeyEnvelope(), valueEnvelope, 256, false);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        StringBuilder trailer = new StringBuilder();
        entry.trailers().forEach(t -> trailer.append(t.value().buffer().getStringWithoutLengthUtf8(t.value().offset(),
            t.value().sizeof())));
        return trailer.toString();
    }

    private KafkaCachePartition newPartition() throws Exception
    {
        Path location = tempFolder.newFolder().toPath();
        KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        return new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
    }

    private static TestModelHandler handler(
        int length)
    {
        return new TestModelHandler(new TestModelConfig(length, emptyList(), true));
    }

    // a model that copies the value through and surfaces it whole under one path, standing in for a real
    // model that surfaces the field an extract transform is configured to watch
    private static ModelHandler extractingHandler(
        String path)
    {
        return handler(new String[] { path, null });
    }

    // a model that copies the value through and surfaces the given path/value pairs as its fields, so a
    // test can place the extracted field among others the traversal also surfaces
    private static ModelHandler fieldsHandler(
        String... pathsAndValues)
    {
        return handler(pathsAndValues);
    }

    // a model whose declared framing padding scales with the length it is given, so a test can prove a
    // reservation is sized from the full declared value length, not just the first DATA fragment
    private static ModelHandler paddingHandler()
    {
        return new ModelHandler()
        {
            @Override
            public ModelPipeline supplyDecoder(
                ModelEnvelope envelope,
                ModelTransform transform,
                ModelCache cache)
            {
                return new PaddingPipeline();
            }

            @Override
            public ModelPipeline supplyEncoder(
                ModelEnvelope envelope,
                ModelTransform transform)
            {
                return supplyDecoder(envelope, transform, ModelCache.NONE);
            }
        };
    }

    private static ModelHandler handler(
        String[] pathsAndValues)
    {
        return new ModelHandler()
        {
            @Override
            public ModelPipeline supplyDecoder(
                ModelEnvelope envelope,
                ModelTransform transform,
                ModelCache cache)
            {
                return new ExtractingPipeline(transform, pathsAndValues);
            }

            @Override
            public ModelPipeline supplyEncoder(
                ModelEnvelope envelope,
                ModelTransform transform)
            {
                return supplyDecoder(envelope, transform, ModelCache.NONE);
            }
        };
    }

    private static KafkaKeyFW key(
        MutableDirectBufferEx buffer,
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        return new KafkaKeyFW.Builder().wrap(buffer, 0, buffer.capacity())
            .length(bytes.length)
            .value(k -> k.set(bytes))
            .build();
    }

    private static Array32FW<KafkaHeaderFW> noHeaders(
        MutableDirectBufferEx buffer,
        int offset)
    {
        return new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
            .wrap(buffer, offset, buffer.capacity())
            .build();
    }

    private static OctetsFW value(
        MutableDirectBufferEx buffer,
        int offset,
        String text)
    {
        return new OctetsFW.Builder()
            .wrap(buffer, offset, buffer.capacity())
            .set(text.getBytes(UTF_8))
            .build();
    }

    private static byte[] bytes(
        OctetsFW octets)
    {
        byte[] result = new byte[octets.sizeof()];
        octets.buffer().getBytes(octets.offset(), result);
        return result;
    }

    private static final class ExtractingPipeline implements ModelPipeline
    {
        private final ModelFieldBridge bridge;
        private final String[] pathsAndValues;
        private final MutableDirectBufferEx field = new UnsafeBufferEx(new byte[64]);
        private final ModelPipelineResult result = new ModelPipelineResult();

        // each pair is a path and the value to surface it with, a null value meaning the whole source
        private ExtractingPipeline(
            ModelTransform transform,
            String[] pathsAndValues)
        {
            this.bridge = new ModelFieldBridge(transform);
            this.pathsAndValues = pathsAndValues;
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            final int srcLength = srcLimit - srcIndex;
            dst.putBytes(dstIndex, src, srcIndex, srcLength);

            bridge.start(authorization);
            for (int index = 0; index < pathsAndValues.length; index += 2)
            {
                final String text = pathsAndValues[index + 1];
                if (text == null)
                {
                    bridge.field(pathsAndValues[index], src, srcIndex, srcLength);
                }
                else
                {
                    final byte[] value = text.getBytes(UTF_8);
                    field.putBytes(0, value);
                    bridge.field(pathsAndValues[index], field, 0, value.length);
                }
            }
            bridge.end();

            return result.set(ModelStatus.COMPLETE, srcLength, srcLength);
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public void reset()
        {
        }
    }

    // a pipeline whose declared padding scales with the length it is given, so a test can distinguish a
    // reservation sized from the first DATA fragment's length from one sized from the full declared value
    private static final class PaddingPipeline implements ModelPipeline
    {
        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            long authorization,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int padding(
            DirectBufferEx data,
            int index,
            int length)
        {
            return length * 2;
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        @Override
        public void reset()
        {
        }
    }

    public static class NodeTest
    {
        @Rule
        public TemporaryFolder tempFolder = new TemporaryFolder();

        @Test
        public void shouldCleanSegment() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaConfiguration config = new KafkaConfiguration();
            KafkaCacheTopicConfig topic = new KafkaCacheTopicConfig(config);

            int slotCapacity = ENGINE_BUFFER_SLOT_CAPACITY.get(config);
            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(slotCapacity * 2));
            MutableInteger entryMark = new MutableInteger(0);
            MutableInteger valueMark = new MutableInteger(0);
            MutableInteger valueLimit = new MutableInteger(0);

            KafkaKeyFW key = new KafkaKeyFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity())
                .length(4)
                .value(k -> k.set("test".getBytes(UTF_8)))
                .build();

            Array32FW<KafkaHeaderFW> headers = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                    .wrap(writeBuffer, key.limit(), writeBuffer.capacity())
                    .item(h -> h.nameLen(6).name(n -> n.set("header".getBytes(UTF_8)))
                                .valueLen(5).value(v -> v.set("value".getBytes(UTF_8))))
                    .build();

            OctetsFW value = new OctetsFW.Builder()
                    .wrap(writeBuffer, headers.limit(), writeBuffer.capacity())
                    .set(new byte[slotCapacity + 1])
                    .build();

            KafkaCacheEntryFW ancestorRO = new KafkaCacheEntryFW();

            KafkaCachePartition partition = new KafkaCachePartition(location, topic, "cache", "test", 0, 65536, long[]::new);
            Node head10 = partition.append(10L);
            KafkaCacheSegment head10s = head10.segment();

            partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
                new KafkaCacheKeyEnvelope(), new KafkaCacheTrailerEnvelope(), 0, false);

            long keyHash = partition.computeKeyHash(key);
            KafkaCacheEntryFW ancestor = head10.findAndMarkAncestor(key, keyHash, 11L, ancestorRO);

            partition.writeEntry(null, 1L, 1L, 0L, 12L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
                new KafkaCacheKeyEnvelope(), new KafkaCacheTrailerEnvelope(), 0, false);

            Node head15 = partition.append(15L);
            KafkaCacheSegment head15s = head15.segment();
            Node tail10 = head15.previous();
            KafkaCacheSegment tail10s = tail10.segment();

            long now = currentTimeMillis();
            tail10s.cleanableAt(now);
            tail10.clean(now);

            KafkaCacheSegment clean10s = tail10.segment();

            assertNotNull(clean10s);
            assertEquals("[KafkaCacheSegment] test[0] @ 10 +0", head10s.toString());
            assertEquals("[KafkaCacheSegment] test[0] @ 10 +0", tail10s.toString());
            assertEquals("[KafkaCacheSegment] test[0] @ 10 +1", clean10s.toString());
            assertEquals("[KafkaCacheSegment] test[0] @ 15 +1", head15s.toString());
        }

        @Test
        public void shouldSeekAncestor() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());

            MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(ByteBuffer.allocate(1024));
            MutableInteger entryMark = new MutableInteger(0);
            MutableInteger valueMark = new MutableInteger(0);
            MutableInteger valueLimit = new MutableInteger(0);

            KafkaKeyFW key = new KafkaKeyFW.Builder().wrap(writeBuffer, 0, writeBuffer.capacity())
                .length(4)
                .value(k -> k.set("test".getBytes(UTF_8)))
                .build();

            Array32FW<KafkaHeaderFW> headers = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                    .wrap(writeBuffer, key.limit(), writeBuffer.capacity())
                    .build();

            OctetsFW value = new OctetsFW.Builder()
                    .wrap(writeBuffer, headers.limit(), 0)
                    .build();

            KafkaCacheEntryFW ancestorRO = new KafkaCacheEntryFW();

            KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
            Node head10 = partition.append(10L);

            partition.writeEntry(null, 1L, 1L, 0L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
                new KafkaCacheKeyEnvelope(), new KafkaCacheTrailerEnvelope(), 0, false);

            long keyHash = partition.computeKeyHash(key);
            KafkaCacheEntryFW ancestor = head10.findAndMarkAncestor(key, keyHash, 11L, ancestorRO);

            partition.writeEntry(null, 1L, 1L, 0L, 12L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
                new KafkaCacheKeyEnvelope(), new KafkaCacheTrailerEnvelope(), 0, false);

            Node head15 = partition.append(15L);
            Node tail10 = head15.previous();

            Node seek10 = head15.seekAncestor(10L);

            assertEquals(seek10, tail10);
        }

        @Test
        public void shouldDescribeObject() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());

            KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
            Node node10 = partition.append(10L);

            assertEquals("[Node] 10", node10.toString());
        }

        @Test
        public void shouldDescribeSentinel() throws Exception
        {
            Path location = tempFolder.newFolder().toPath();
            KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());

            KafkaCachePartition partition = new KafkaCachePartition(location, config, "cache", "test", 0, 65536, long[]::new);
            Node sentinel = partition.sentinel();

            assertEquals("[Node] sentinel", sentinel.toString());
        }
    }
}
