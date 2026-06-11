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
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteOrder;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroValues.Entry;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroDecoderTest
{
    private Recorder decode(
        String schemaText,
        byte[] binary)
    {
        Recorder recorder = AvroValues.record(StreamingAvro.schema(schemaText), binary);
        assertEquals(COMPLETE, recorder.status);
        return recorder;
    }

    @Test
    public void shouldDecodeInt()
    {
        Entry entry = decode("\"int\"", new byte[] { 0x02 }).entries.get(0);
        assertEquals(INT, entry.event);
        assertEquals(1, entry.intValue);
    }

    @Test
    public void shouldDecodeNegativeInt()
    {
        assertEquals(-64, decode("\"int\"", new byte[] { 0x7f }).entries.get(0).intValue);
    }

    @Test
    public void shouldDecodeMultiByteInt()
    {
        assertEquals(64, decode("\"int\"", new byte[] { (byte) 0x80, 0x01 }).entries.get(0).intValue);
    }

    @Test
    public void shouldDecodeLong()
    {
        Entry entry = decode("\"long\"", new byte[] { 0x02 }).entries.get(0);
        assertEquals(LONG, entry.event);
        assertEquals(1L, entry.longValue);
    }

    @Test
    public void shouldDecodeBoolean()
    {
        Entry entry = decode("\"boolean\"", new byte[] { 0x01 }).entries.get(0);
        assertEquals(BOOLEAN, entry.event);
        assertEquals(true, entry.booleanValue);
    }

    @Test
    public void shouldDecodeNull()
    {
        assertEquals(NULL, decode("\"null\"", new byte[] {}).entries.get(0).event);
    }

    @Test
    public void shouldDecodeFloat()
    {
        UnsafeBuffer encoded = new UnsafeBuffer(new byte[4]);
        encoded.putFloat(0, 1.5f, ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[4];
        encoded.getBytes(0, bytes);
        Entry entry = decode("\"float\"", bytes).entries.get(0);
        assertEquals(FLOAT, entry.event);
        assertEquals(1.5f, entry.floatValue, 0.0f);
    }

    @Test
    public void shouldDecodeDouble()
    {
        UnsafeBuffer encoded = new UnsafeBuffer(new byte[8]);
        encoded.putDouble(0, 2.25d, ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[8];
        encoded.getBytes(0, bytes);
        Entry entry = decode("\"double\"", bytes).entries.get(0);
        assertEquals(DOUBLE, entry.event);
        assertEquals(2.25d, entry.doubleValue, 0.0d);
    }

    @Test
    public void shouldDecodeString()
    {
        Entry entry = decode("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f }).entries.get(0);
        assertEquals(STRING, entry.event);
        assertEquals("foo", new String(entry.bytes, UTF_8));
    }

    @Test
    public void shouldDecodeRecord()
    {
        List<AvroEvent> events = decode(
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[" +
                "{\"name\":\"id\",\"type\":\"int\"}," +
                "{\"name\":\"name\",\"type\":\"string\"}]}",
            new byte[] { 0x02, 0x04, 0x68, 0x69 }).events;
        assertEquals(List.of(RECORD_START, FIELD_NAME, INT, FIELD_NAME, STRING, RECORD_END), events);
    }

    @Test
    public void shouldDecodeArray()
    {
        List<AvroEvent> events = decode(
            "{\"type\":\"array\",\"items\":\"int\"}",
            new byte[] { 0x04, 0x02, 0x04, 0x00 }).events;
        assertEquals(List.of(ARRAY_START, INT, INT, ARRAY_END), events);
    }

    @Test
    public void shouldDecodeMap()
    {
        Recorder recorder = decode(
            "{\"type\":\"map\",\"values\":\"long\"}",
            new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 });
        assertEquals(List.of(MAP_START, MAP_KEY, LONG, MAP_END), recorder.events);
        assertEquals(7L, recorder.entries.get(2).longValue);
    }

    @Test
    public void shouldDecodeEnum()
    {
        Entry entry = decode(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\"]}",
            new byte[] { 0x02 }).entries.get(0);
        assertEquals(ENUM, entry.event);
        assertEquals(1, entry.intValue);
        assertEquals("HEARTS", entry.string);
    }

    @Test
    public void shouldDecodeUnionNull()
    {
        Recorder recorder = decode("[\"null\",\"string\"]", new byte[] { 0x00 });
        assertEquals(List.of(UNION_BRANCH, NULL), recorder.events);
        assertEquals(0, recorder.entries.get(0).intValue);
    }

    @Test
    public void shouldDecodeUnionString()
    {
        Recorder recorder = decode("[\"null\",\"string\"]", new byte[] { 0x02, 0x02, 0x78 });
        assertEquals(List.of(UNION_BRANCH, STRING), recorder.events);
        assertEquals(1, recorder.entries.get(0).intValue);
    }
}
