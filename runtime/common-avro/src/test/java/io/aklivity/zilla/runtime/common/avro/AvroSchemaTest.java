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

import static io.aklivity.zilla.runtime.common.avro.AvroEvent.END_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.START_ARRAY;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroSchemaTest
{
    private Status parse(
        String schemaText,
        byte[] binary,
        AvroSink sink)
    {
        AvroPipeline pipeline = Avro.schema(schemaText).parser().stream().into(sink);
        pipeline.reset();
        return pipeline.feed(new UnsafeBuffer(binary), 0, binary.length);
    }

    @Test
    public void shouldCompileLogicalTypeOnLong()
    {
        Recorder recorder = AvroValues.record(
            Avro.schema("{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}"),
            new byte[] { 0x02 });
        assertEquals(COMPLETE, recorder.status);
        assertEquals(1L, recorder.entries.get(0).longValue);
    }

    @Test
    public void shouldCompileLogicalTypeOnBytes()
    {
        Recorder recorder = AvroValues.record(
            Avro.schema("{\"type\":\"bytes\",\"logicalType\":\"decimal\"}"),
            new byte[] { 0x02, 0x07 });
        assertEquals(COMPLETE, recorder.status);
    }

    @Test
    public void shouldCompileLogicalTypeOnFixed()
    {
        Recorder recorder = AvroValues.record(
            Avro.schema("{\"type\":\"fixed\",\"name\":\"Dec\",\"size\":2,\"logicalType\":\"decimal\"}"),
            new byte[] { 0x01, 0x02 });
        assertEquals(COMPLETE, recorder.status);
    }

    @Test
    public void shouldResolveNamedTypeReference()
    {
        Recorder recorder = AvroValues.record(
            Avro.schema("""
                {"type":"record","name":"Pair","namespace":"ns","fields":[
                {"name":"a","type":{"type":"enum","name":"E","symbols":["X","Y"]}},
                {"name":"b","type":"E"}]}"""),
            new byte[] { 0x02, 0x00 });
        assertEquals(COMPLETE, recorder.status);
    }

    @Test
    public void shouldParseArrayWithNegativeBlockCount()
    {
        // block count -2 (0x03), block size (0x04), items 1 (0x02) and 2 (0x04), terminator 0x00
        List<AvroEvent> events = AvroValues.parse(
            Avro.schema("{\"type\":\"array\",\"items\":\"int\"}"),
            new byte[] { 0x03, 0x04, 0x02, 0x04, 0x00 });
        assertEquals(List.of(START_ARRAY, INT, INT, END_ARRAY), events);
    }

    @Test
    public void shouldReadStringValueViaGetString()
    {
        String[] captured = new String[1];
        AvroSink sink = (control, source, event) ->
        {
            if (event == AvroEvent.STRING)
            {
                captured[0] = source.getString();
            }
            return Status.PENDING;
        };
        assertEquals(COMPLETE, parse("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f }, sink));
        assertEquals("foo", captured[0]);
    }

    @Test
    public void shouldExposeBytesView()
    {
        byte[][] captured = new byte[1][];
        AvroSink sink = (control, source, event) ->
        {
            if (event == AvroEvent.BYTES)
            {
                DirectBuffer segment = source.getSegment();
                byte[] dst = new byte[segment.capacity()];
                segment.getBytes(0, dst);
                captured[0] = dst;
            }
            return Status.PENDING;
        };
        assertEquals(COMPLETE, parse("\"bytes\"", new byte[] { 0x04, (byte) 0xff, 0x01 }, sink));
        assertArrayEquals(new byte[] { (byte) 0xff, 0x01 }, captured[0]);
    }

    @Test
    public void shouldParseStringUtf8Multibyte()
    {
        byte[] euro = "€".getBytes(UTF_8);
        byte[] binary = new byte[1 + euro.length];
        binary[0] = (byte) (euro.length << 1);
        System.arraycopy(euro, 0, binary, 1, euro.length);
        String[] captured = new String[1];
        AvroSink sink = (control, source, event) ->
        {
            if (event == AvroEvent.STRING)
            {
                captured[0] = source.getString();
            }
            return Status.PENDING;
        };
        assertEquals(COMPLETE, parse("\"string\"", binary, sink));
        assertEquals("€", captured[0]);
    }

    @Test
    public void shouldReadRecordFieldNamesViaGetField()
    {
        List<String> fields = new ArrayList<>();
        AvroSink sink = (control, source, event) ->
        {
            if (event == AvroEvent.FIELD_NAME)
            {
                fields.add(source.getField());
            }
            return Status.PENDING;
        };
        assertEquals(COMPLETE, parse("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""",
            new byte[] { 0x02, 0x04, 0x68, 0x69 }, sink));
        assertEquals(List.of("id", "name"), fields);
    }

    @Test
    public void shouldReadMapKeysViaGetKey()
    {
        List<String> keys = new ArrayList<>();
        AvroSink sink = (control, source, event) ->
        {
            if (event == AvroEvent.MAP_KEY)
            {
                keys.add(source.getKey());
            }
            return Status.PENDING;
        };
        // one block, count 1 (0x02), key "a" (0x02 'a'), value 7 (0x0e), terminator 0x00
        assertEquals(COMPLETE, parse("{\"type\":\"map\",\"values\":\"long\"}",
            new byte[] { 0x02, 0x02, 0x61, 0x0e, 0x00 }, sink));
        assertEquals(List.of("a"), keys);
    }

    @Test
    public void shouldReportLocation()
    {
        int[] depth = { -1 };
        long[] position = { -1L };
        AvroSink sink = (control, source, event) ->
        {
            if (event == AvroEvent.STRING)
            {
                AvroLocation location = source.getLocation();
                depth[0] = location.depth();
                position[0] = location.position();
            }
            return Status.PENDING;
        };
        // record { id:int=1 (0x02), name:string="hi" (0x04 'h' 'i') }; name begins at byte 1, depth 2
        assertEquals(COMPLETE, parse("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""",
            new byte[] { 0x02, 0x04, 0x68, 0x69 }, sink));
        assertEquals(2, depth[0]);
        assertEquals(1L, position[0]);
    }

    @Test
    public void shouldRejectMalformedSchemaDocument()
    {
        assertThrows(AvroValidationException.class, () -> Avro.schema("{ this is not json"));
    }

    @Test
    public void shouldRejectUnexpectedSchemaNode()
    {
        assertThrows(AvroValidationException.class, () -> Avro.schema("123"));
    }
}
