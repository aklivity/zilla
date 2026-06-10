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

import static io.aklivity.zilla.runtime.common.avro.AvroDecodePipeline.Status.COMPLETE;
import static io.aklivity.zilla.runtime.common.avro.AvroDecodePipeline.Status.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroFragmentedDecoderTest
{
    private final Recorder recorder = new Recorder();

    private List<AvroEvent> decodeWhole(
        AvroSchema schema,
        byte[] binary)
    {
        AvroDecodePipeline decoder = schema.decoder(recorder);
        recorder.reset();
        decoder.reset();
        UnsafeBuffer buffer = new UnsafeBuffer(binary);
        assertEquals(COMPLETE, decoder.feed(buffer, 0, binary.length));
        return recorder.entries().stream().map(e -> e.event).collect(Collectors.toList());
    }

    private List<AvroEvent> decodeByteByByte(
        AvroSchema schema,
        byte[] binary)
    {
        AvroDecodePipeline decoder = schema.decoder(recorder);
        recorder.reset();
        decoder.reset();
        UnsafeBuffer one = new UnsafeBuffer(new byte[1]);
        AvroDecodePipeline.Status status = binary.length == 0 ? decoder.feed(one, 0, 0) : PENDING;
        for (int i = 0; i < binary.length; i++)
        {
            one.putByte(0, binary[i]);
            status = decoder.feed(one, 0, 1);
        }
        assertEquals(COMPLETE, status);
        return recorder.entries().stream().map(e -> e.event).collect(Collectors.toList());
    }

    private void assertSameFragmentedAsWhole(
        String schemaText,
        byte[] binary)
    {
        AvroSchema schema = StreamingAvro.schema(schemaText);
        List<AvroEvent> whole = decodeWhole(schema, binary);
        List<AvroEvent> fragmented = decodeByteByByte(schema, binary);
        assertEquals(whole, fragmented);
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
        encoded.putDouble(0, 2.25d, java.nio.ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[8];
        encoded.getBytes(0, bytes);
        assertSameFragmentedAsWhole("\"double\"", bytes);
    }
}
