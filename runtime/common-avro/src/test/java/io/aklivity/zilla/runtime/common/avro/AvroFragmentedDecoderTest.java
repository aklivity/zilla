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
package io.aklivity.zilla.runtime.common.avro;

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteOrder;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroFragmentedDecoderTest
{
    private List<AvroEvent> decodeByteByByte(
        AvroSchema schema,
        byte[] binary)
    {
        Recorder recorder = new Recorder();
        AvroPipeline pipeline = schema.decoder().stream().into(recorder);
        pipeline.reset();
        UnsafeBuffer one = new UnsafeBuffer(new byte[1]);
        Status status = binary.length == 0 ? pipeline.feed(one, 0, 0) : Status.PENDING;
        for (int i = 0; i < binary.length; i++)
        {
            one.putByte(0, binary[i]);
            status = pipeline.feed(one, 0, 1);
        }
        assertEquals(COMPLETE, status);
        return recorder.events;
    }

    private void assertSameFragmentedAsWhole(
        String schemaText,
        byte[] binary)
    {
        AvroSchema schema = StreamingAvro.schema(schemaText);
        Recorder whole = AvroValues.record(schema, binary);
        assertEquals(COMPLETE, whole.status);
        assertEquals(whole.events, decodeByteByByte(schema, binary));
    }

    @Test
    public void shouldDecodeMultiByteIntAcrossFrames()
    {
        assertSameFragmentedAsWhole("\"int\"", new byte[] { (byte) 0x80, 0x01 });
    }

    @Test
    public void shouldDecodeStringAcrossFrames()
    {
        assertSameFragmentedAsWhole("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f });
    }

    @Test
    public void shouldDecodeRecordAcrossFrames()
    {
        assertSameFragmentedAsWhole(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[" +
                "{\"name\":\"id\",\"type\":\"int\"}," +
                "{\"name\":\"name\",\"type\":\"string\"}]}",
            new byte[] { 0x02, 0x04, 0x68, 0x69 });
    }

    @Test
    public void shouldDecodeArrayAcrossFrames()
    {
        assertSameFragmentedAsWhole(
            "{\"type\":\"array\",\"items\":\"int\"}",
            new byte[] { 0x04, 0x02, 0x04, 0x00 });
    }

    @Test
    public void shouldDecodeMapAcrossFrames()
    {
        assertSameFragmentedAsWhole(
            "{\"type\":\"map\",\"values\":\"long\"}",
            new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 });
    }

    @Test
    public void shouldDecodeDoubleAcrossFrames()
    {
        UnsafeBuffer encoded = new UnsafeBuffer(new byte[8]);
        encoded.putDouble(0, 2.25d, ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[8];
        encoded.getBytes(0, bytes);
        assertSameFragmentedAsWhole("\"double\"", bytes);
    }

    @Test
    public void shouldDecodeSegmentedAcrossFrames()
    {
        // a verbatim segment run spanning frames must reproduce the input exactly
        AvroSchema schema = StreamingAvro.schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[" +
                "{\"name\":\"id\",\"type\":\"int\"}," +
                "{\"name\":\"name\",\"type\":\"string\"}]}");
        byte[] binary = new byte[] { (byte) 0x80, 0x01, 0x08, 0x77, 0x78, 0x79, 0x7a };

        UnsafeBuffer out = new UnsafeBuffer(new byte[64]);
        AvroEncoder encoder = schema.encoder(out, 0);
        AvroPipeline pipeline = schema.decoder().stream().into(AvroSink.of(encoder, AvroSink.Delivery.SEGMENTABLE));
        pipeline.reset();
        UnsafeBuffer one = new UnsafeBuffer(new byte[1]);
        Status status = Status.PENDING;
        for (int i = 0; i < binary.length; i++)
        {
            one.putByte(0, binary[i]);
            status = pipeline.feed(one, 0, 1);
        }
        assertEquals(COMPLETE, status);
        byte[] result = new byte[encoder.length()];
        out.getBytes(0, result);
        assertArrayEquals(binary, result);
    }
}
