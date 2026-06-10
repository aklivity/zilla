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
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.BOOLEAN;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.DOUBLE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ENUM;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIELD_NAME;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FLOAT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.LONG;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_END;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_KEY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_START;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.NULL;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.RECORD_END;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.RECORD_START;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.STRING;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.UNION_BRANCH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Entry;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroDecoderTest
{
    private final Recorder recorder = new Recorder();

    private List<AvroEvent> decode(
        AvroSchema schema,
        byte[] binary)
    {
        AvroDecodePipeline decoder = schema.decoder(recorder);
        recorder.reset();
        decoder.reset();
        UnsafeBuffer buffer = new UnsafeBuffer(binary);
        AvroDecodePipeline.Status status = decoder.feed(buffer, 0, binary.length);
        assertEquals(COMPLETE, status);
        return recorder.entries().stream().map(e -> e.event).collect(Collectors.toList());
    }

    @Test
    public void shouldDecodeInt()
    {
        AvroSchema schema = StreamingAvro.schema("\"int\"");
        // zig-zag varint: 1 -> 0x02
        decode(schema, new byte[] { 0x02 });
        assertEquals(INT, recorder.entries().get(0).event);
        assertEquals(1, recorder.entries().get(0).intValue);
    }

    @Test
    public void shouldDecodeNegativeInt()
    {
        AvroSchema schema = StreamingAvro.schema("\"int\"");
        // zig-zag varint: -64 -> 0x7f
        decode(schema, new byte[] { 0x7f });
        assertEquals(-64, recorder.entries().get(0).intValue);
    }

    @Test
    public void shouldDecodeMultiByteInt()
    {
        AvroSchema schema = StreamingAvro.schema("\"int\"");
        // zig-zag varint: 64 -> 0x80 0x01
        decode(schema, new byte[] { (byte) 0x80, 0x01 });
        assertEquals(64, recorder.entries().get(0).intValue);
    }

    @Test
    public void shouldDecodeLong()
    {
        AvroSchema schema = StreamingAvro.schema("\"long\"");
        decode(schema, new byte[] { 0x02 });
        assertEquals(LONG, recorder.entries().get(0).event);
        assertEquals(1L, recorder.entries().get(0).longValue);
    }

    @Test
    public void shouldDecodeBoolean()
    {
        AvroSchema schema = StreamingAvro.schema("\"boolean\"");
        decode(schema, new byte[] { 0x01 });
        assertEquals(BOOLEAN, recorder.entries().get(0).event);
        assertEquals(true, recorder.entries().get(0).booleanValue);
    }

    @Test
    public void shouldDecodeNull()
    {
        AvroSchema schema = StreamingAvro.schema("\"null\"");
        decode(schema, new byte[] {});
        assertEquals(NULL, recorder.entries().get(0).event);
    }

    @Test
    public void shouldDecodeFloat()
    {
        AvroSchema schema = StreamingAvro.schema("\"float\"");
        UnsafeBuffer encoded = new UnsafeBuffer(new byte[4]);
        encoded.putFloat(0, 1.5f, java.nio.ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[4];
        encoded.getBytes(0, bytes);
        decode(schema, bytes);
        assertEquals(FLOAT, recorder.entries().get(0).event);
        assertEquals(1.5f, recorder.entries().get(0).floatValue, 0.0f);
    }

    @Test
    public void shouldDecodeDouble()
    {
        AvroSchema schema = StreamingAvro.schema("\"double\"");
        UnsafeBuffer encoded = new UnsafeBuffer(new byte[8]);
        encoded.putDouble(0, 2.25d, java.nio.ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[8];
        encoded.getBytes(0, bytes);
        decode(schema, bytes);
        assertEquals(DOUBLE, recorder.entries().get(0).event);
        assertEquals(2.25d, recorder.entries().get(0).doubleValue, 0.0d);
    }

    @Test
    public void shouldDecodeString()
    {
        AvroSchema schema = StreamingAvro.schema("\"string\"");
        // length 3 -> 0x06, then "foo"
        byte[] bytes = new byte[] { 0x06, 0x66, 0x6f, 0x6f };
        decode(schema, bytes);
        Entry entry = recorder.entries().get(0);
        assertEquals(STRING, entry.event);
        assertEquals("foo", new String(entry.bytes, UTF_8));
    }

    @Test
    public void shouldDecodeRecord()
    {
        AvroSchema schema = StreamingAvro.schema(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[" +
                "{\"name\":\"id\",\"type\":\"int\"}," +
                "{\"name\":\"name\",\"type\":\"string\"}]}");
        // id=1 (0x02), name="hi" (0x04 'h' 'i')
        byte[] bytes = new byte[] { 0x02, 0x04, 0x68, 0x69 };
        List<AvroEvent> events = decode(schema, bytes);
        assertEquals(List.of(RECORD_START, FIELD_NAME, INT, FIELD_NAME, STRING, RECORD_END), events);
    }

    @Test
    public void shouldDecodeArray()
    {
        AvroSchema schema = StreamingAvro.schema("{\"type\":\"array\",\"items\":\"int\"}");
        // one block, count 2 (0x04), items 1 (0x02) and 2 (0x04), terminator 0x00
        byte[] bytes = new byte[] { 0x04, 0x02, 0x04, 0x00 };
        List<AvroEvent> events = decode(schema, bytes);
        assertEquals(List.of(ARRAY_START, INT, INT, ARRAY_END), events);
    }

    @Test
    public void shouldDecodeMap()
    {
        AvroSchema schema = StreamingAvro.schema("{\"type\":\"map\",\"values\":\"long\"}");
        // one block, count 1 (0x02), key "a" (0x02 'a'), value 7 (0x0e), terminator 0x00
        byte[] bytes = new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 };
        List<AvroEvent> events = decode(schema, bytes);
        assertEquals(List.of(MAP_START, MAP_KEY, LONG, MAP_END), events);
        assertEquals(7L, recorder.entries().get(2).longValue);
    }

    @Test
    public void shouldDecodeEnum()
    {
        AvroSchema schema = StreamingAvro.schema(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\"]}");
        // index 1 -> 0x02
        decode(schema, new byte[] { 0x02 });
        Entry entry = recorder.entries().get(0);
        assertEquals(ENUM, entry.event);
        assertEquals(1, entry.intValue);
        assertEquals("HEARTS", entry.string);
    }

    @Test
    public void shouldDecodeUnionNull()
    {
        AvroSchema schema = StreamingAvro.schema("[\"null\",\"string\"]");
        // branch 0 -> 0x00 (null)
        List<AvroEvent> events = decode(schema, new byte[] { 0x00 });
        assertEquals(List.of(UNION_BRANCH, NULL), events);
        assertEquals(0, recorder.entries().get(0).intValue);
    }

    @Test
    public void shouldDecodeUnionString()
    {
        AvroSchema schema = StreamingAvro.schema("[\"null\",\"string\"]");
        // branch 1 -> 0x02, then string "x" (0x02 'x')
        List<AvroEvent> events = decode(schema, new byte[] { 0x02, 0x02, 0x78 });
        assertEquals(List.of(UNION_BRANCH, STRING), events);
        assertEquals(1, recorder.entries().get(0).intValue);
    }
}
