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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.avro.AvroPipeline.Status;

class AvroPipelineTransformTest
{
    // non-zero offsets on both src and dst so a transform that conflates absolute position with length
    // (e.g. treating the dst limit as a length, or producing relative to 0) is caught; the invariants are
    // 0 <= consumed <= srcLimit - srcOffset and 0 <= produced <= dstLimit - dstOffset
    private static final int SRC_OFFSET = 5;
    private static final int DST_OFFSET = 7;
    private static final int GUARD = 1000;

    @Test
    void shouldTransformWholeValueIntoDestination()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(schema.validator())
            .into(generatorFor(schema));
        pipeline.reset();

        // "foo": length 3 (zigzag varint 0x06) followed by the three bytes
        byte[] in = { 0x06, 0x66, 0x6f, 0x6f };
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        AvroPipelineResult result = pipeline.transform(srcOf(in), SRC_OFFSET, SRC_OFFSET + in.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);

        assertEquals(Status.COMPLETED, result.status());
        assertEquals(in.length, result.consumed());
        assertTrue(result.produced() <= dstCap);
        byte[] out = new byte[result.produced()];
        dst.getBytes(DST_OFFSET, out);
        assertArrayEquals(in, out);
    }

    @Test
    void shouldSuspendOnBoundedOutputThenCompleteAcrossDrains()
    {
        AvroSchema schema = Avro.schema("\"string\"");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(schema.validator())
            .into(generatorFor(schema));
        pipeline.reset();

        // a string value longer than the destination bound forces SUSPENDED, streaming the value across
        // drains; round-trip reproduces the input bytes exactly
        int length = 40;
        byte[] in = new byte[1 + length];
        in[0] = (byte) (length << 1);
        Arrays.fill(in, 1, in.length, (byte) 'x');
        byte[] drained = drainToCompletion(pipeline, in, 16);

        assertArrayEquals(in, drained);
    }

    @Test
    void shouldStarveThenCompleteAcrossInputWindows()
    {
        AvroSchema schema = Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""");
        AvroPipeline pipeline = Avro.stream(Avro.parser(schema))
            .transform(schema.validator())
            .into(generatorFor(schema));
        pipeline.reset();

        // id=1 (0x02) in the first window, name="hi" (0x04 0x68 0x69) in the second
        byte[] f1 = { 0x02 };
        byte[] f2 = { 0x04, 0x68, 0x69 };
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();

        AvroPipelineResult first = pipeline.transform(srcOf(f1), SRC_OFFSET, SRC_OFFSET + f1.length, false,
            dst, DST_OFFSET, DST_OFFSET + dstCap);
        assertEquals(Status.STARVED, first.status());
        assertTrue(first.produced() <= dstCap);
        drainChunk(dst, first.produced(), drained);

        AvroPipelineResult second = pipeline.transform(srcOf(f2), SRC_OFFSET, SRC_OFFSET + f2.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);
        assertEquals(Status.COMPLETED, second.status());
        assertTrue(second.produced() <= dstCap);
        drainChunk(dst, second.produced(), drained);

        assertArrayEquals(new byte[] { 0x02, 0x04, 0x68, 0x69 }, drained.toByteArray());
    }

    private static AvroGenerator generatorFor(
        AvroSchema schema)
    {
        // the pipeline owns and re-targets this generator per transform call; the buffer here is a
        // throwaway placeholder, replaced by the caller's destination on each transform
        return Avro.generator(schema, new UnsafeBufferEx(new byte[1]), 0);
    }

    private byte[] drainToCompletion(
        AvroPipeline pipeline,
        byte[] in,
        int dstCap)
    {
        MutableDirectBuffer src = srcOf(in);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();
        AvroPipelineResult result;
        int guard = 0;
        do
        {
            result = pipeline.transform(src, SRC_OFFSET, SRC_OFFSET + in.length, true, dst, DST_OFFSET, DST_OFFSET + dstCap);
            // the transform contract: never produce or consume more than the supplied window
            assertTrue(result.produced() <= dstCap);
            assertTrue(result.consumed() <= in.length);
            drainChunk(dst, result.produced(), drained);
            guard++;
        }
        while (result.status() == Status.SUSPENDED && guard < GUARD);

        assertEquals(Status.COMPLETED, result.status());
        return drained.toByteArray();
    }

    private static void drainChunk(
        MutableDirectBuffer dst,
        int produced,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(DST_OFFSET, chunk);
        sink.writeBytes(chunk);
    }

    private static MutableDirectBuffer srcOf(
        byte[] bytes)
    {
        MutableDirectBuffer buffer = new UnsafeBufferEx(new byte[SRC_OFFSET + bytes.length]);
        buffer.putBytes(SRC_OFFSET, bytes);
        return buffer;
    }
}
