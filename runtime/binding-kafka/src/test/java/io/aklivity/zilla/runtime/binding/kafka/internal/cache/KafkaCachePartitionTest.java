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
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import org.agrona.collections.MutableInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicHeaderType;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaTopicTransformsType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
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
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelVisitor;
import io.aklivity.zilla.runtime.engine.test.internal.model.TestModelHandler;
import io.aklivity.zilla.runtime.engine.test.internal.model.config.TestModelConfig;

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

        KafkaCacheModel transformValue = KafkaCacheModel.decoder(handler(5), emptySet(), scratch);

        partition.writeEntry(null, 1L, 1L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue, false, null);

        KafkaCacheEntryFW entry = head.segment().logFile().readBytes(entryMark.value, entryRO::wrap);
        assertNotEquals(-1, entry.convertedPosition());

        KafkaCachePaddedValueFW transformed = head.segment().convertedFile()
            .readBytes(entry.convertedPosition(), paddedValueRO::wrap);
        assertEquals(5, transformed.length());
        assertArrayEquals("hello".getBytes(UTF_8), bytes(transformed.value()));
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

        KafkaCacheModel transformValue = KafkaCacheModel.decoder(handler(99), emptySet(), scratch);

        partition.writeEntry(null, 1L, 1L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue, false, null);

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

        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType("$.key",
            singletonList(new KafkaTopicHeaderType("region", "$.region")));

        KafkaExtractor keyExtractor = new KafkaExtractor(singleton("$.key"));
        KafkaCacheModel transformKey =
            new KafkaCacheModel(new ExtractingPipeline(keyExtractor, "$.key"), keyExtractor, scratch);
        KafkaExtractor valueExtractor = new KafkaExtractor(singleton("$.region"));
        KafkaCacheModel transformValue =
            new KafkaCacheModel(new ExtractingPipeline(valueExtractor, "$.region"), valueExtractor, scratch);

        partition.writeEntry(null, 1L, 1L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, transformKey, transformValue, false, transforms);

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
    public void shouldIsolateInterleavedStreams() throws Exception
    {
        KafkaCachePartition partition = newPartition();
        Node head = partition.append(10L);
        MutableInteger entryMark = new MutableInteger(0);
        MutableInteger valueMark = new MutableInteger(0);
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

        KafkaTopicTransformsType transforms = new KafkaTopicTransformsType(null,
            singletonList(new KafkaTopicHeaderType("region", "$.region")));

        KafkaExtractor extractorA = new KafkaExtractor(singleton("$.region"));
        KafkaCacheModel modelA =
            new KafkaCacheModel(new ExtractingPipeline(extractorA, "$.region"), extractorA, scratch);
        KafkaExtractor extractorB = new KafkaExtractor(singleton("$.region"));
        KafkaCacheModel modelB =
            new KafkaCacheModel(new ExtractingPipeline(extractorB, "$.region"), extractorB, scratch);

        assertEquals("AAA", writeAndReadTrailer(partition, head, entryMark, valueMark, buffer, 11L, "AAA", modelA, transforms));
        assertEquals("BBBB", writeAndReadTrailer(partition, head, entryMark, valueMark, buffer, 12L, "BBBB", modelB, transforms));
        assertEquals("CC", writeAndReadTrailer(partition, head, entryMark, valueMark, buffer, 13L, "CC", modelA, transforms));
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
        KafkaTopicTransformsType transforms)
    {
        KafkaKeyFW key = key(buffer, "k");
        Array32FW<KafkaHeaderFW> headers = noHeaders(buffer, key.limit());
        OctetsFW value = value(buffer, headers.limit(), valueText);

        partition.writeEntry(null, 1L, 1L, offset, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, transformValue, false, transforms);

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
        private final ModelVisitor visitor;
        private final String path;
        private final ModelPipelineResult result = new ModelPipelineResult();

        private ExtractingPipeline(
            ModelVisitor visitor,
            String path)
        {
            this.visitor = visitor;
            this.path = path;
        }

        @Override
        public ModelPipelineResult transform(
            long traceId,
            long bindingId,
            int flags,
            DirectBufferEx src,
            int srcIndex,
            int srcLimit,
            MutableDirectBufferEx dst,
            int dstIndex,
            int dstLimit)
        {
            int srcLength = srcLimit - srcIndex;
            dst.putBytes(dstIndex, src, srcIndex, srcLength);
            visitor.onField(path, src, srcIndex, srcLength);
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

            partition.writeEntry(null, 1L, 1L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE,
                KafkaCacheModel.NONE, false, null);

            long keyHash = partition.computeKeyHash(key);
            KafkaCacheEntryFW ancestor = head10.findAndMarkAncestor(key, keyHash, 11L, ancestorRO);

            partition.writeEntry(null, 1L, 1L, 12L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE,
                KafkaCacheModel.NONE, false, null);

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

            partition.writeEntry(null, 1L, 1L, 11L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE,
                KafkaCacheModel.NONE, false, null);

            long keyHash = partition.computeKeyHash(key);
            KafkaCacheEntryFW ancestor = head10.findAndMarkAncestor(key, keyHash, 11L, ancestorRO);

            partition.writeEntry(null, 1L, 1L, 12L, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
                key, headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE,
                KafkaCacheModel.NONE, false, null);

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
