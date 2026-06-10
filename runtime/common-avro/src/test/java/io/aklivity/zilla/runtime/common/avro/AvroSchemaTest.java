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
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ARRAY_END;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ARRAY_START;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroSchemaTest
{
    private final Recorder recorder = new Recorder();

    private AvroDecodePipeline.Status decode(
        AvroSchema schema,
        AvroSink sink,
        byte[] binary)
    {
        AvroDecodePipeline decoder = schema.decoder(sink);
        decoder.reset();
        UnsafeBuffer buffer = new UnsafeBuffer(binary);
        return decoder.feed(buffer, 0, binary.length);
    }

    @Test
    public void shouldCompileLogicalTypeOnLong()
    {
        AvroSchema schema = StreamingAvro.schema("{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}");
        assertEquals(COMPLETE, decode(schema, recorder, new byte[] { 0x02 }));
        assertEquals(1L, recorder.entries().get(0).longValue);
    }

    @Test
    public void shouldCompileLogicalTypeOnBytes()
    {
        AvroSchema schema = StreamingAvro.schema("{\"type\":\"bytes\",\"logicalType\":\"decimal\"}");
        assertEquals(COMPLETE, decode(schema, recorder, new byte[] { 0x02, 0x07 }));
    }

    @Test
    public void shouldCompileLogicalTypeOnFixed()
    {
        AvroSchema schema = StreamingAvro.schema(
            "{\"type\":\"fixed\",\"name\":\"Dec\",\"size\":2,\"logicalType\":\"decimal\"}");
        assertEquals(COMPLETE, decode(schema, recorder, new byte[] { 0x01, 0x02 }));
    }

    @Test
    public void shouldResolveNamedTypeReference()
    {
        AvroSchema schema = StreamingAvro.schema(
            "{\"type\":\"record\",\"name\":\"Pair\",\"namespace\":\"ns\",\"fields\":[" +
                "{\"name\":\"a\",\"type\":{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"X\",\"Y\"]}}," +
                "{\"name\":\"b\",\"type\":\"E\"}]}");
        // a=1 (0x02), b=0 (0x00)
        assertEquals(COMPLETE, decode(schema, recorder, new byte[] { 0x02, 0x00 }));
    }

    @Test
    public void shouldDecodeArrayWithNegativeBlockCount()
    {
        AvroSchema schema = StreamingAvro.schema("{\"type\":\"array\",\"items\":\"int\"}");
        // block count -2 (0x03), block size 2 bytes (0x04), items 1 (0x02) and 2 (0x04), terminator 0x00
        byte[] bytes = new byte[] { 0x03, 0x04, 0x02, 0x04, 0x00 };
        List<AvroEvent> events = new ArrayList<>();
        AvroSink sink = (event, in) ->
        {
            events.add(event);
            return AvroDecodePipeline.Status.PENDING;
        };
        assertEquals(COMPLETE, decode(schema, sink, bytes));
        assertEquals(List.of(ARRAY_START, INT, INT, ARRAY_END), events);
    }

    @Test
    public void shouldReadStringValueViaGetString()
    {
        AvroSchema schema = StreamingAvro.schema("\"string\"");
        String[] captured = new String[1];
        AvroSink sink = (event, in) ->
        {
            captured[0] = in.getString();
            return AvroDecodePipeline.Status.PENDING;
        };
        assertEquals(COMPLETE, decode(schema, sink, new byte[] { 0x06, 0x66, 0x6f, 0x6f }));
        assertEquals("foo", captured[0]);
    }

    @Test
    public void shouldExposeBytesView()
    {
        AvroSchema schema = StreamingAvro.schema("\"bytes\"");
        byte[][] captured = new byte[1][];
        AvroSink sink = (event, in) ->
        {
            byte[] dst = new byte[in.length()];
            in.buffer().getBytes(in.offset(), dst);
            captured[0] = dst;
            return AvroDecodePipeline.Status.PENDING;
        };
        assertEquals(COMPLETE, decode(schema, sink, new byte[] { 0x04, (byte) 0xff, 0x01 }));
        assertArrayEquals(new byte[] { (byte) 0xff, 0x01 }, captured[0]);
    }

    @Test
    public void shouldRejectMalformedSchemaDocument()
    {
        assertThrows(AvroValidationException.class, () -> StreamingAvro.schema("{ this is not json"));
    }

    @Test
    public void shouldRejectUnexpectedSchemaNode()
    {
        assertThrows(AvroValidationException.class, () -> StreamingAvro.schema("123"));
    }

    @Test
    public void shouldDecodeStringUtf8Multibyte()
    {
        AvroSchema schema = StreamingAvro.schema("\"string\"");
        byte[] euro = "€".getBytes(UTF_8);
        byte[] bytes = new byte[1 + euro.length];
        bytes[0] = (byte) (euro.length << 1);
        System.arraycopy(euro, 0, bytes, 1, euro.length);
        String[] captured = new String[1];
        AvroSink sink = (event, in) ->
        {
            captured[0] = in.getString();
            return AvroDecodePipeline.Status.PENDING;
        };
        assertEquals(COMPLETE, decode(schema, sink, bytes));
        assertEquals("€", captured[0]);
    }
}
