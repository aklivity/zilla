/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.specs.binding.mqtt.kafka.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.nio.ByteBuffer;
import java.util.function.IntConsumer;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetsFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttPublishOffsetMetadataFW;
import io.aklivity.zilla.specs.binding.mqtt.kafka.internal.types.MqttSubscribeOffsetMetadataFW;

public class MqttKafkaFunctionsTest
{
    @Test
    public void shouldGetMapper()
    {
        MqttKafkaFunctions.Mapper mapper = new MqttKafkaFunctions.Mapper();
        assertEquals("mqtt_kafka", mapper.getPrefixName());
    }
    @Test
    public void shouldEncodeMqttOffsetMetadataV1()
    {
        final String state = MqttKafkaFunctions.subscribeMetadata()
            .v1()
                .metadata(10)
                .metadata(14)
                .metadata(15)
                .build()
            .build();

        final IntArrayList metadataList = new IntArrayList();
        UnsafeBufferEx buffer = new UnsafeBufferEx(BitUtil.fromHex(state));
        MqttSubscribeOffsetMetadataFW offsetMetadata = new MqttSubscribeOffsetMetadataFW().wrap(buffer, 0, buffer.capacity());
        offsetMetadata.subscribeMetadataV1().packetIds().forEachRemaining((IntConsumer) metadataList::add);

        assertEquals(10, (int) metadataList.get(0));
        assertEquals(14, (int) metadataList.get(1));
        assertEquals(15, (int) metadataList.get(2));
    }

    @Test
    public void shouldEncodeMqttOffsetMetadataV2()
    {
        final String state = MqttKafkaFunctions.subscribeMetadata()
            .v2()
                .metadata(10)
                .metadata(14)
                .metadata(15)
                .build()
            .build();

        final IntArrayList metadataList = new IntArrayList();
        UnsafeBufferEx buffer = new UnsafeBufferEx(BitUtil.fromHex(state));
        MqttSubscribeOffsetMetadataFW offsetMetadata = new MqttSubscribeOffsetMetadataFW().wrap(buffer, 0, buffer.capacity());
        offsetMetadata.subscribeMetadataV2().packetIds().forEachRemaining((IntConsumer) metadataList::add);

        assertEquals(10, (int) metadataList.get(0));
        assertEquals(14, (int) metadataList.get(1));
        assertEquals(15, (int) metadataList.get(2));
    }

    @Test
    public void shouldEncodeMqttPublishOffsetMetadata()
    {
        final String state = MqttKafkaFunctions.publishMetadata()
            .producer(1L, (short) 1)
            .packetId(1)
            .build();

        DirectBuffer buffer = new UnsafeBufferEx(BitUtil.fromHex(state));
        MqttPublishOffsetMetadataFW offsetMetadata = new MqttPublishOffsetMetadataFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(1, offsetMetadata.version());
        assertEquals(1, offsetMetadata.packetIds().nextInt());
        assertEquals(1, offsetMetadata.producerId());
        assertEquals(1, offsetMetadata.producerEpoch());
    }

    @Test
    public void shouldBuildSessionOffsets()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
                .packetId(10)
                .packetId(20)
            .entry("sensor/two", 2, 200L, 43L, 8, 4)
            .build();

        DirectBuffer buffer = new UnsafeBufferEx(bytes);
        MqttKafkaSessionOffsetsFW sessionOffsets = new MqttKafkaSessionOffsetsFW().wrap(buffer, 0, buffer.capacity());

        assertEquals(1, sessionOffsets.version());

        final IntArrayList topics = new IntArrayList();
        final IntArrayList firstPacketIds = new IntArrayList();
        final IntArrayList[] holder = new IntArrayList[] { new IntArrayList() };

        MqttKafkaSessionOffsetMetadataFW[] capture = new MqttKafkaSessionOffsetMetadataFW[2];
        int[] index = new int[] { 0 };
        sessionOffsets.entries().forEach(entry ->
        {
            MqttKafkaSessionOffsetMetadataFW copy = new MqttKafkaSessionOffsetMetadataFW();
            copy.wrap(entry.buffer(), entry.offset(), entry.limit());
            capture[index[0]++] = copy;
            topics.add(entry.partitionId());
        });

        assertEquals(2, index[0]);

        MqttKafkaSessionOffsetMetadataFW first = capture[0];
        assertEquals("sensor/one", first.topic().asString());
        assertEquals(0, first.partitionId());
        assertEquals(100L, first.consumedOffset());
        assertEquals(42L, first.producerId());
        assertEquals(7, first.producerEpoch());
        assertEquals(3, first.producerSequence());
        first.packetIds().forEachRemaining((IntConsumer) firstPacketIds::add);
        assertEquals(10, (int) firstPacketIds.get(0));
        assertEquals(20, (int) firstPacketIds.get(1));

        MqttKafkaSessionOffsetMetadataFW second = capture[1];
        assertEquals("sensor/two", second.topic().asString());
        assertEquals(2, second.partitionId());
        assertEquals(200L, second.consumedOffset());
        assertEquals(43L, second.producerId());
        assertEquals(8, second.producerEpoch());
        assertEquals(4, second.producerSequence());
        assertNull(second.packetIds());

        assertEquals(holder.length, 1);
    }

    @Test
    public void shouldMatchSessionOffsets() throws Exception
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
                .packetId(10)
                .packetId(20)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
                .packetId(10)
                .packetId(20)
            .build();

        ByteBuffer matched = (ByteBuffer) matcher.match(ByteBuffer.wrap(bytes));

        assertEquals(bytes.length, matched.position());
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnMismatch()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .version(1)
            .entry("sensor/two", 0, 100L, 42L, 7, 3)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldIgnoreSessionOffsetsWhenNoConstraints() throws Exception
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .build();

        assertNull(matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldMatchSessionOffsetsByVersionOnly() throws Exception
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .version(1)
            .build();

        ByteBuffer matched = (ByteBuffer) matcher.match(ByteBuffer.wrap(bytes));

        assertEquals(bytes.length, matched.position());
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnVersion()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .version(2)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnEntryCount()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .entry("sensor/two", 1, 200L, 43L, 8, 4)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnPartitionId()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .entry("sensor/one", 9, 100L, 42L, 7, 3)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnConsumedOffset()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .entry("sensor/one", 0, 999L, 42L, 7, 3)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnProducerId()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .entry("sensor/one", 0, 100L, 99L, 7, 3)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnProducerEpoch()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .entry("sensor/one", 0, 100L, 42L, 9, 3)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnProducerSequence()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .entry("sensor/one", 0, 100L, 42L, 7, 9)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsOnPacketIds()
    {
        final byte[] bytes = MqttKafkaFunctions.sessionOffsets()
            .version(1)
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
                .packetId(10)
            .build();

        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .entry("sensor/one", 0, 100L, 42L, 7, 3)
                .packetId(20)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.wrap(bytes)));
    }

    @Test
    public void shouldNotMatchSessionOffsetsWhenBufferEmpty()
    {
        BytesMatcher matcher = MqttKafkaFunctions.matchSessionOffsets()
            .version(1)
            .build();

        assertThrows(Exception.class, () -> matcher.match(ByteBuffer.allocate(0)));
    }
}
