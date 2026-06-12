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
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.SUSPENDED;
import static io.aklivity.zilla.runtime.common.avro.AvroSink.Delivery.STRUCTURED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;

public class AvroChunkingTest
{
    private static final String SCHEMA = """
        {"type":"record","name":"R","fields":[
        {"name":"id","type":"long"},
        {"name":"name","type":"string"},
        {"name":"tags","type":{"type":"array","items":"string"}},
        {"name":"props","type":{"type":"map","values":"long"}},
        {"name":"opt","type":["null","string"]}]}""";

    @Test
    public void shouldReportRemainingAgainstLimit()
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[64]);
        AvroGenerator generator = Avro.generator(Avro.schema("\"int\""), out, 0);
        generator.wrap(out, 0, 10);
        assertEquals(10, generator.remaining());
        generator.writeInt(1);
        assertEquals(1, generator.length());
        assertEquals(9, generator.remaining());
    }

    @Test
    public void shouldRejectLimitExceedingCapacity()
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[16]);
        AvroGenerator generator = Avro.generator(Avro.schema("\"int\""), out, 0);
        assertThrows(IllegalArgumentException.class, () -> generator.wrap(out, 0, out.capacity() + 1));
    }

    @Test
    public void shouldChunkStructuredDatumAcrossSuspends()
    {
        AvroSchema schema = Avro.schema(SCHEMA);
        byte[] datum = referenceEncode();

        // unchunked structured re-encode is the reference output
        byte[] whole = AvroValues.transcode(schema, datum, STRUCTURED);

        int limit = 24;
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = Avro.parser(schema).stream().into(AvroSink.of(generator));

        generator.wrap(out, 0, limit);
        pipeline.reset();

        List<byte[]> chunks = new ArrayList<>();
        UnsafeBuffer in = new UnsafeBuffer(datum);
        Status status = pipeline.feed(in, 0, datum.length);
        while (status == SUSPENDED)
        {
            chunks.add(drain(out, generator.length()));
            generator.wrap(out, 0, limit);
            status = pipeline.feed(in, 0, datum.length);
        }
        assertEquals(COMPLETE, status);
        chunks.add(drain(out, generator.length()));

        assertTrue(chunks.size() >= 2, "expected the datum to span multiple chunks");
        for (byte[] chunk : chunks)
        {
            assertTrue(chunk.length <= limit, "chunk exceeded the generator limit");
        }
        assertArrayEquals(whole, concat(chunks));
    }

    private static byte[] drain(
        MutableDirectBuffer buffer,
        int length)
    {
        byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static byte[] concat(
        List<byte[]> chunks)
    {
        int length = 0;
        for (byte[] chunk : chunks)
        {
            length += chunk.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] chunk : chunks)
        {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    private static byte[] referenceEncode()
    {
        byte[] bytes;
        try
        {
            Schema schema = new Schema.Parser().parse(SCHEMA);
            GenericData.Record record = new GenericData.Record(schema);
            record.put("id", 1234567890L);
            record.put("name", "neo");
            record.put("tags", new GenericData.Array<>(schema.getField("tags").schema(), List.of("alpha", "bravo", "charlie")));
            record.put("props", Map.of("x", 1L, "y", 2L));
            record.put("opt", "trinity");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new GenericDatumWriter<Object>(schema).write(record, encoder);
            encoder.flush();
            bytes = out.toByteArray();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return bytes;
    }
}
