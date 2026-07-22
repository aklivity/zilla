/*
 * Copyright 2021-2026 Aklivity Inc
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

import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.COMPLETED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.REJECTED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.STARVED;
import static io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status.SUSPENDED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;

public class AvroValueStreamingTest
{
    @Test
    public void shouldStreamBytesLargerThanInputWindow()
    {
        AvroSchema schema = Avro.schema("\"bytes\"");
        byte[] payload = new byte[100];
        for (int i = 0; i < payload.length; i++)
        {
            payload[i] = (byte) i;
        }
        byte[] datum = encode("\"bytes\"", ByteBuffer.wrap(payload));

        Collector collector = new Collector();
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(collector);
        Status status = feedWindowed(pipeline, datum, 16, collector);

        assertEquals(COMPLETED, status);
        assertTrue(collector.chunks.size() >= 2, "expected the value to arrive in chunks");
        assertEquals(0, collector.deferreds.get(collector.deferreds.size() - 1).intValue());
        assertArrayEquals(payload, collector.bytes());
    }

    @Test
    public void shouldReportDeferredBytesCountingDownToZero()
    {
        AvroSchema schema = Avro.schema("\"bytes\"");
        byte[] payload = new byte[50];
        byte[] datum = encode("\"bytes\"", ByteBuffer.wrap(payload));

        Collector collector = new Collector();
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(collector);
        assertEquals(COMPLETED, feedWindowed(pipeline, datum, 12, collector));

        // each chunk's deferred is the payload still to come after it, so it equals the total minus the
        // bytes delivered so far, counting down to zero on the final chunk
        int written = 0;
        for (int i = 0; i < collector.chunks.size(); i++)
        {
            written += collector.chunks.get(i).length;
            assertEquals(payload.length - written, collector.deferreds.get(i).intValue());
        }
        assertEquals(payload.length, written);
    }

    @Test
    public void shouldStreamStringOnUtf8BoundariesAcrossWindow()
    {
        // a string peppered with 3-byte euro signs so chunk cuts fall inside multi-byte characters
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 20; i++)
        {
            builder.append("ab€cd");
        }
        String text = builder.toString();
        AvroSchema schema = Avro.schema("\"string\"");
        byte[] datum = encode("\"string\"", text);

        Collector collector = new Collector();
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(collector);
        assertEquals(COMPLETED, feedWindowed(pipeline, datum, 8, collector));

        assertTrue(collector.chunks.size() >= 2, "expected the value to arrive in chunks");
        // every chunk decodes cleanly (no character split across chunks) and they concatenate to the whole
        StringBuilder decoded = new StringBuilder();
        for (byte[] chunk : collector.chunks)
        {
            String piece = new String(chunk, UTF_8);
            assertEquals(piece, new String(piece.getBytes(UTF_8), UTF_8), "chunk split a UTF-8 character");
            decoded.append(piece);
        }
        assertEquals(text, decoded.toString());
    }

    @Test
    public void shouldTranscodeValueLargerThanInputWindowAndOutputBound()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 64; i++)
        {
            builder.append("lorem-ipsum-");
        }
        byte[] datum = encode("\"string\"", builder.toString());

        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[16]);
        AvroGenerator generator = Avro.generator(schema, out, 0);
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(AvroSink.of(generator));

        // input arrives in a 16-byte sliding window; output is bounded to 16 bytes and drained on suspend,
        // so a value far larger than either buffer streams straight through
        List<byte[]> output = new ArrayList<>();
        pipeline.reset();
        generator.wrap(out, 0, out.capacity());
        UnsafeBufferEx in = new UnsafeBufferEx(datum);
        int progress = 0;
        int length = 0;
        Status status = pipeline.transform(in, 0, 0, false);
        while (status != COMPLETED && status != REJECTED)
        {
            if (status == SUSPENDED)
            {
                output.add(drain(out, generator.length()));
                generator.wrap(out, 0, out.capacity());
            }
            else
            {
                // STARVED: drop the progress prefix of the last window, keeping its unconsumed tail
                progress += length - pipeline.remaining();
            }
            length = Math.min(16, datum.length - progress);
            status = pipeline.transform(in, progress, progress + length, progress + length == datum.length);
        }
        assertEquals(COMPLETED, status);
        output.add(drain(out, generator.length()));

        assertArrayEquals(datum, concat(output));
    }

    @Test
    public void shouldReportSegmentDeferredUnboundedUntilFinal()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        byte[] datum = encode("\"string\"", "a".repeat(40));

        List<Integer> deferreds = new ArrayList<>();
        AvroSink collector = new AvroSink()
        {
            @Override
            public Status transform(
                AvroController control,
                AvroSource source,
                AvroEvent event)
            {
                Status status = Status.ADVANCED;
                if (event == AvroEvent.START_MESSAGE)
                {
                    control.segmentable();
                }
                else if (event == AvroEvent.SEGMENT)
                {
                    deferreds.add(source.deferredBytes());
                }
                else if (event == AvroEvent.END_MESSAGE)
                {
                    status = Status.COMPLETED;
                }
                return status;
            }

            @Override
            public boolean identity()
            {
                return false;
            }
        };

        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(collector);
        pipeline.reset();
        UnsafeBufferEx buffer = new UnsafeBufferEx(datum);
        Status status = pipeline.transform(buffer, 0, 0, false);
        int progress = 0;
        int guard = datum.length * 2 + 8;
        while (status != COMPLETED && status != REJECTED && guard-- > 0)
        {
            int length = Math.min(8, datum.length - progress);
            status = pipeline.transform(buffer, progress, progress + length, progress + length == datum.length);
            progress += length - pipeline.remaining();
        }
        assertEquals(COMPLETED, status);

        assertTrue(deferreds.size() >= 2, "expected the datum to span multiple segments");
        for (int i = 0; i < deferreds.size() - 1; i++)
        {
            assertEquals(AvroSource.UNBOUNDED, deferreds.get(i).intValue());
        }
        assertEquals(0, deferreds.get(deferreds.size() - 1).intValue());
    }

    @Test
    public void shouldReportStarvedWhenWindowConsumedMidDatum()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        byte[] datum = encode("\"string\"", "a".repeat(40));

        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(new Collector());
        pipeline.reset();
        // a partial window with more input to follow (last == false) — starved, not truncated
        Status status = pipeline.transform(new UnsafeBufferEx(datum), 0, 10, false);

        assertEquals(STARVED, status);
    }

    @Test
    public void shouldRejectTruncatedValueOnLastWindow()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        byte[] datum = encode("\"string\"", "a".repeat(40));

        AvroPipeline pipeline = Avro.stream(Avro.parser(schema)).into(new Collector());
        pipeline.reset();
        // the final window holds only part of the value, so it cannot complete -> truncated
        Status status = pipeline.transform(new UnsafeBufferEx(datum), 0, 10, true);

        assertEquals(REJECTED, status);
    }

    private static Status feedWindowed(
        AvroPipeline pipeline,
        byte[] datum,
        int window,
        Collector collector)
    {
        pipeline.reset();
        collector.reset();
        UnsafeBufferEx buffer = new UnsafeBufferEx(datum);
        Status status = pipeline.transform(buffer, 0, 0, false);
        int progress = 0;
        int guard = datum.length * 2 + 8;
        while (status != COMPLETED && status != REJECTED && guard-- > 0)
        {
            int length = Math.min(window, datum.length - progress);
            status = pipeline.transform(buffer, progress, progress + length, progress + length == datum.length);
            progress += length - pipeline.remaining();
        }
        return status;
    }

    private static byte[] drain(
        MutableDirectBufferEx buffer,
        int length)
    {
        byte[] bytes = new byte[length];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static byte[] concat(
        List<byte[]> parts)
    {
        int length = 0;
        for (byte[] part : parts)
        {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts)
        {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static byte[] encode(
        String schemaText,
        Object value)
    {
        byte[] bytes;
        try
        {
            Schema schema = new Schema.Parser().parse(schemaText);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new GenericDatumWriter<Object>(schema).write(value, encoder);
            encoder.flush();
            bytes = out.toByteArray();
        }
        catch (IOException ex)
        {
            throw new UncheckedIOException(ex);
        }
        return bytes;
    }

    private static final class Collector implements AvroSink
    {
        private final List<byte[]> chunks = new ArrayList<>();
        private final List<Integer> deferreds = new ArrayList<>();

        @Override
        public Status transform(
            AvroController control,
            AvroSource source,
            AvroEvent event)
        {
            if (event == AvroEvent.STRING || event == AvroEvent.BYTES || event == AvroEvent.FIXED)
            {
                DirectBufferEx segment = source.getSegment();
                byte[] chunk = new byte[segment.capacity()];
                segment.getBytes(0, chunk);
                chunks.add(chunk);
                deferreds.add(source.deferredBytes());
            }
            return event == AvroEvent.END_MESSAGE ? Status.COMPLETED : Status.ADVANCED;
        }

        @Override
        public void reset()
        {
            chunks.clear();
            deferreds.clear();
        }

        @Override
        public boolean identity()
        {
            return false;
        }

        private byte[] bytes()
        {
            return concat(chunks);
        }
    }
}
