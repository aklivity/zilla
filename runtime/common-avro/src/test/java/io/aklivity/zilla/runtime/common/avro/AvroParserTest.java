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

import static io.aklivity.zilla.runtime.common.avro.AvroEvent.BOOLEAN;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.DOUBLE;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_MAP;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.ENUM;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FIELD_NAME;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.FLOAT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.LONG;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.MAP_KEY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.NULL;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_MAP;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_RECORD;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.STRING;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.UNION_BRANCH;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Entry;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroParserTest
{
    private Recorder parse(
        String schemaText,
        byte[] binary)
    {
        Recorder recorder = AvroValues.record(Avro.schema(schemaText), binary);
        assertEquals(COMPLETED, recorder.status);
        return recorder;
    }

    @Test
    public void shouldParseInt()
    {
        Entry entry = parse("\"int\"", new byte[] { 0x02 }).entries.get(0);
        assertEquals(INT, entry.event);
        assertEquals(1, entry.intValue);
    }

    @Test
    public void shouldParseNegativeInt()
    {
        assertEquals(-64, parse("\"int\"", new byte[] { 0x7f }).entries.get(0).intValue);
    }

    @Test
    public void shouldParseMultiByteInt()
    {
        assertEquals(64, parse("\"int\"", new byte[] { (byte) 0x80, 0x01 }).entries.get(0).intValue);
    }

    @Test
    public void shouldParseLong()
    {
        Entry entry = parse("\"long\"", new byte[] { 0x02 }).entries.get(0);
        assertEquals(LONG, entry.event);
        assertEquals(1L, entry.longValue);
    }

    @Test
    public void shouldParseBoolean()
    {
        Entry entry = parse("\"boolean\"", new byte[] { 0x01 }).entries.get(0);
        assertEquals(BOOLEAN, entry.event);
        assertEquals(true, entry.booleanValue);
    }

    @Test
    public void shouldParseNull()
    {
        assertEquals(NULL, parse("\"null\"", new byte[] {}).entries.get(0).event);
    }

    @Test
    public void shouldParseFloat()
    {
        UnsafeBufferEx encoded = new UnsafeBufferEx(new byte[4]);
        encoded.putFloat(0, 1.5f, ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[4];
        encoded.getBytes(0, bytes);
        Entry entry = parse("\"float\"", bytes).entries.get(0);
        assertEquals(FLOAT, entry.event);
        assertEquals(1.5f, entry.floatValue, 0.0f);
    }

    @Test
    public void shouldParseDouble()
    {
        UnsafeBufferEx encoded = new UnsafeBufferEx(new byte[8]);
        encoded.putDouble(0, 2.25d, ByteOrder.LITTLE_ENDIAN);
        byte[] bytes = new byte[8];
        encoded.getBytes(0, bytes);
        Entry entry = parse("\"double\"", bytes).entries.get(0);
        assertEquals(DOUBLE, entry.event);
        assertEquals(2.25d, entry.doubleValue, 0.0d);
    }

    @Test
    public void shouldParseString()
    {
        Entry entry = parse("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f }).entries.get(0);
        assertEquals(STRING, entry.event);
        assertEquals("foo", new String(entry.bytes, UTF_8));
    }

    @Test
    public void shouldParseRecord()
    {
        List<AvroEvent> events = parse("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""",
            new byte[] { 0x02, 0x04, 0x68, 0x69 }).events;
        assertEquals(List.of(START_RECORD, FIELD_NAME, INT, FIELD_NAME, STRING, END_RECORD), events);
    }

    @Test
    public void shouldParseArray()
    {
        List<AvroEvent> events = parse(
            "{\"type\":\"array\",\"items\":\"int\"}",
            new byte[] { 0x04, 0x02, 0x04, 0x00 }).events;
        assertEquals(List.of(START_ARRAY, INT, INT, END_ARRAY), events);
    }

    @Test
    public void shouldParseMap()
    {
        Recorder recorder = parse(
            "{\"type\":\"map\",\"values\":\"long\"}",
            new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 });
        assertEquals(List.of(START_MAP, MAP_KEY, LONG, END_MAP), recorder.events);
        assertEquals(7L, recorder.entries.get(2).longValue);
    }

    @Test
    public void shouldParseEnum()
    {
        Entry entry = parse(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\"]}",
            new byte[] { 0x02 }).entries.get(0);
        assertEquals(ENUM, entry.event);
        assertEquals(1, entry.intValue);
        assertEquals("HEARTS", entry.string);
    }

    @Test
    public void shouldParseUnionNull()
    {
        Recorder recorder = parse("[\"null\",\"string\"]", new byte[] { 0x00 });
        assertEquals(List.of(UNION_BRANCH, NULL), recorder.events);
        assertEquals(0, recorder.entries.get(0).intValue);
    }

    @Test
    public void shouldParseUnionString()
    {
        Recorder recorder = parse("[\"null\",\"string\"]", new byte[] { 0x02, 0x02, 0x78 });
        assertEquals(List.of(UNION_BRANCH, STRING), recorder.events);
        assertEquals(1, recorder.entries.get(0).intValue);
    }
}
