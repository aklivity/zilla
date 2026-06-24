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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.IntArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaPublishMetadata.KafkaOffsetMetadata;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.stream.MqttKafkaPublishMetadata.KafkaSessionOffsetsHelper;
import io.aklivity.zilla.runtime.binding.mqtt.kafka.internal.types.MqttKafkaSessionOffsetsFW;

public class MqttKafkaSessionOffsetsHelperTest
{
    @Test
    public void shouldRoundTripSessionOffsets()
    {
        final KafkaSessionOffsetsHelper helper = new KafkaSessionOffsetsHelper(new UnsafeBuffer(new byte[2048]));

        final List<KafkaOffsetMetadata> input = new ArrayList<>();

        final IntArrayList packetIds0 = new IntArrayList();
        packetIds0.add(1);
        packetIds0.add(2);
        packetIds0.add(3);
        final KafkaOffsetMetadata entry0 = new KafkaOffsetMetadata("sensor/one", 100L, (short) 5, packetIds0);
        entry0.sequence = 42L;
        entry0.partitionId = 0;
        entry0.producerSequence = 7;
        input.add(entry0);

        final IntArrayList packetIds1 = new IntArrayList();
        final KafkaOffsetMetadata entry1 = new KafkaOffsetMetadata("sensor/two", 200L, (short) 6, packetIds1);
        entry1.sequence = 99L;
        entry1.partitionId = 3;
        entry1.producerSequence = 0;
        input.add(entry1);

        final IntArrayList packetIds2 = new IntArrayList();
        packetIds2.add(11);
        packetIds2.add(22);
        final KafkaOffsetMetadata entry2 = new KafkaOffsetMetadata("sensor/three", 300L, (short) 7, packetIds2);
        entry2.sequence = 1234567890123L;
        entry2.partitionId = 9;
        entry2.producerSequence = 15;
        input.add(entry2);

        final MqttKafkaSessionOffsetsFW encoded = helper.encode(input);

        final MqttKafkaSessionOffsetsFW sessionOffsetsRO = new MqttKafkaSessionOffsetsFW();
        sessionOffsetsRO.wrap(encoded.buffer(), encoded.offset(), encoded.limit());

        assertEquals(1, sessionOffsetsRO.version());

        final List<KafkaOffsetMetadata> output = helper.decode(sessionOffsetsRO);

        assertEquals(input.size(), output.size());
        for (int i = 0; i < input.size(); i++)
        {
            final KafkaOffsetMetadata expected = input.get(i);
            final KafkaOffsetMetadata actual = output.get(i);

            assertEquals(expected.topic, actual.topic);
            assertEquals(expected.partitionId, actual.partitionId);
            assertEquals(expected.sequence, actual.sequence);
            assertEquals(expected.producerId, actual.producerId);
            assertEquals(expected.producerEpoch, actual.producerEpoch);
            assertEquals(expected.producerSequence, actual.producerSequence);
            assertEquals(expected.packetIds, actual.packetIds);
        }
    }
}
