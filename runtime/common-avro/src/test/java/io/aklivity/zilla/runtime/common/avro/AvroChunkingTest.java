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
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
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
    public void shouldRejectNonSplittableValueExceedingLimit()
    {
        AvroSchema schema = Avro.schema("\"double\"");
        MutableDirectBuffer out = new UnsafeBuffer(new byte[64]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        generator.wrap(out, 0, 4);
        AvroPipeline pipeline = Avro.parser(schema).stream().into(AvroSink.of(generator));
        pipeline.reset();
        // a double is 8 bytes and cannot be split, so it does not fit the 4-byte limit even in a fresh
        // buffer — rather than write past the limit, the datum is rejected
        byte[] datum = new byte[8];
        Status status = pipeline.feed(new UnsafeBuffer(datum), 0, datum.length);
        assertEquals(REJECTED, status);
    }

    @Test
    public void shouldFeedEachEventOnceThroughTransformDespiteSuspends()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        byte[] datum = new byte[41];
        datum[0] = 0x50;
        for (int i = 0; i < 40; i++)
        {
            datum[i + 1] = (byte) ('a' + i % 26);
        }

        int[] feeds = { 0 };
        AvroTransform counting = (control, source, event, sink) ->
        {
            feeds[0]++;
            return sink.feed(control, source, event);
        };

        int limit = 8;
        MutableDirectBuffer out = new UnsafeBuffer(new byte[256]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = Avro.parser(schema).stream().transform(counting).into(AvroSink.of(generator));
        generator.wrap(out, 0, limit);
        pipeline.reset();

        UnsafeBuffer in = new UnsafeBuffer(datum);
        Status status = pipeline.feed(in, 0, datum.length);
        while (status == SUSPENDED)
        {
            generator.wrap(out, 0, limit);
            status = pipeline.feed(in, 0, datum.length);
        }
        assertEquals(COMPLETE, status);

        // a suspended value resumes through the sink, not by replaying events, so the transform sees
        // START_MESSAGE and STRING once each (the datum completes on the value, before END_MESSAGE) —
        // not the STRING re-fed once per chunk as event replay would
        assertEquals(2, feeds[0]);
    }

    @Test
    public void shouldStreamStringLargerThanLimitAcrossSuspends()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        // a 40-byte string value: 0x50 length prefix (zigzag 40) then 40 payload bytes
        byte[] datum = new byte[41];
        datum[0] = 0x50;
        for (int i = 0; i < 40; i++)
        {
            datum[i + 1] = (byte) ('a' + i % 26);
        }
        byte[] whole = AvroValues.transcode(schema, datum, STRUCTURED);

        int limit = 8;
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

        assertTrue(chunks.size() >= 2, "expected the value to be split across chunks");
        for (byte[] chunk : chunks)
        {
            assertTrue(chunk.length <= limit, "chunk exceeded the generator limit");
        }
        assertArrayEquals(whole, concat(chunks));
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
