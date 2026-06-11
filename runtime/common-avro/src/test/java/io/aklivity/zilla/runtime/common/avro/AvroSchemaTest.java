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
import static io.aklivity.zilla.runtime.common.avro.AvroEvent.INT;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;
import io.aklivity.zilla.runtime.common.avro.AvroValues.Recorder;

public class AvroSchemaTest
{
    private Status decode(
        String schemaText,
        byte[] binary,
        AvroSink sink)
    {
        AvroPipeline pipeline = StreamingAvro.schema(schemaText).decode().into(sink);
        pipeline.reset();
        return pipeline.feed(new UnsafeBuffer(binary), 0, binary.length);
    }

    @Test
    public void shouldCompileLogicalTypeOnLong()
    {
        Recorder recorder = AvroValues.record(
            StreamingAvro.schema("{\"type\":\"long\",\"logicalType\":\"timestamp-millis\"}"),
            new byte[] { 0x02 });
        assertEquals(COMPLETE, recorder.status);
        assertEquals(1L, recorder.entries.get(0).longValue);
    }

    @Test
    public void shouldCompileLogicalTypeOnBytes()
    {
        Recorder recorder = AvroValues.record(
            StreamingAvro.schema("{\"type\":\"bytes\",\"logicalType\":\"decimal\"}"),
            new byte[] { 0x02, 0x07 });
        assertEquals(COMPLETE, recorder.status);
    }

    @Test
    public void shouldCompileLogicalTypeOnFixed()
    {
        Recorder recorder = AvroValues.record(
            StreamingAvro.schema("{\"type\":\"fixed\",\"name\":\"Dec\",\"size\":2,\"logicalType\":\"decimal\"}"),
            new byte[] { 0x01, 0x02 });
        assertEquals(COMPLETE, recorder.status);
    }

    @Test
    public void shouldResolveNamedTypeReference()
    {
        Recorder recorder = AvroValues.record(
            StreamingAvro.schema(
                "{\"type\":\"record\",\"name\":\"Pair\",\"namespace\":\"ns\",\"fields\":[" +
                    "{\"name\":\"a\",\"type\":{\"type\":\"enum\",\"name\":\"E\",\"symbols\":[\"X\",\"Y\"]}}," +
                    "{\"name\":\"b\",\"type\":\"E\"}]}"),
            new byte[] { 0x02, 0x00 });
        assertEquals(COMPLETE, recorder.status);
    }

    @Test
    public void shouldDecodeArrayWithNegativeBlockCount()
    {
        // block count -2 (0x03), block size (0x04), items 1 (0x02) and 2 (0x04), terminator 0x00
        List<AvroEvent> events = AvroValues.decode(
            StreamingAvro.schema("{\"type\":\"array\",\"items\":\"int\"}"),
            new byte[] { 0x03, 0x04, 0x02, 0x04, 0x00 });
        assertEquals(List.of(ARRAY_START, INT, INT, ARRAY_END), events);
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
        assertEquals(COMPLETE, decode("\"string\"", new byte[] { 0x06, 0x66, 0x6f, 0x6f }, sink));
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
                byte[] dst = new byte[source.length()];
                source.buffer().getBytes(source.offset(), dst);
                captured[0] = dst;
            }
            return Status.PENDING;
        };
        assertEquals(COMPLETE, decode("\"bytes\"", new byte[] { 0x04, (byte) 0xff, 0x01 }, sink));
        assertArrayEquals(new byte[] { (byte) 0xff, 0x01 }, captured[0]);
    }

    @Test
    public void shouldDecodeStringUtf8Multibyte()
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
        assertEquals(COMPLETE, decode("\"string\"", binary, sink));
        assertEquals("€", captured[0]);
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
}
