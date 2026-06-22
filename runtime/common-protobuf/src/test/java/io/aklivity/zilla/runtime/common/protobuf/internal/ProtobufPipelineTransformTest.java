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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipeline.Status;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufPipelineResult;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;
import io.aklivity.zilla.runtime.common.protobuf.json.ProtobufJson;

class ProtobufPipelineTransformTest
{
    // non-zero offsets on both src and dst so a transform that conflates absolute position with length
    // (e.g. treating the dst limit as a length, or producing relative to 0) is caught; the invariants are
    // 0 <= consumed <= srcLimit - srcOffset and 0 <= produced <= dstLimit - dstOffset
    private static final int SRC_OFFSET = 5;
    private static final int DST_OFFSET = 7;
    private static final int GUARD = 1000;

    @Test
    void shouldTransformWholeMessageIntoDestination()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser()).into(Protobuf.generator());
        pipeline.reset();

        byte[] in = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
        });
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ProtobufPipelineResult result = pipeline.transform(srcOf(in), SRC_OFFSET, SRC_OFFSET + in.length, true,
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
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser()).into(Protobuf.generator());
        pipeline.reset();

        // a LEN field longer than the destination bound forces SUSPENDED, streaming the value across drains;
        // the schema-free copy reproduces the input bytes exactly
        byte[] value = new byte[40];
        Arrays.fill(value, (byte) 'x');
        byte[] in = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(value);
        });
        byte[] drained = drainToCompletion(pipeline, in, 16);

        assertArrayEquals(in, drained);
    }

    @Test
    void shouldStarveThenCompleteAcrossInputWindows()
    {
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser()).into(Protobuf.generator());
        pipeline.reset();

        // field 1 (varint) in the first window, field 2 (len-delimited "hi") in the second, split at the
        // field boundary so the first window is fully consumed before the message starves
        byte[] f1 = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.VARINT);
            w.writeVarint64(5);
        });
        byte[] f2 = wire(w ->
        {
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
        });
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();

        ProtobufPipelineResult first = pipeline.transform(srcOf(f1), SRC_OFFSET, SRC_OFFSET + f1.length, false,
            dst, DST_OFFSET, DST_OFFSET + dstCap);
        assertEquals(Status.STARVED, first.status());
        assertTrue(first.produced() <= dstCap);
        drainChunk(dst, first.produced(), drained);

        ProtobufPipelineResult second = pipeline.transform(srcOf(f2), SRC_OFFSET, SRC_OFFSET + f2.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);
        assertEquals(Status.COMPLETED, second.status());
        assertTrue(second.produced() <= dstCap);
        drainChunk(dst, second.produced(), drained);

        byte[] expected = new byte[f1.length + f2.length];
        System.arraycopy(f1, 0, expected, 0, f1.length);
        System.arraycopy(f2, 0, expected, f1.length, f2.length);
        assertArrayEquals(expected, drained.toByteArray());
    }

    @Test
    void shouldTransformTypedRoundTripIntoDestination()
    {
        ProtobufSchema schema = newSchema();
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .into(Protobuf.generator(), schema, "P");
        pipeline.reset();

        // fields in field-number order so a same-schema re-encode reproduces the input bytes exactly
        byte[] in = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
        });
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ProtobufPipelineResult result = pipeline.transform(srcOf(in), SRC_OFFSET, SRC_OFFSET + in.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);

        assertEquals(Status.COMPLETED, result.status());
        assertEquals(in.length, result.consumed());
        assertTrue(result.produced() <= dstCap);
        byte[] out = new byte[result.produced()];
        dst.getBytes(DST_OFFSET, out);
        assertArrayEquals(in, out);
    }

    @Test
    void shouldTransformProtobufToJsonDocument()
    {
        ProtobufSchema schema = newSchema();
        JsonGeneratorEx json = JsonEx.createGenerator();
        // the JSON terminal writes its closing brace in flush(), which transform must call on COMPLETED so
        // the produced document is well-formed (the wire generator's flush is a no-op at completion)
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "P"))
            .into(ProtobufJson.generator(json, schema, "P"), schema, "P");
        pipeline.reset();

        byte[] in = wire(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("neo".getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(5);
        });
        int dstCap = 128;
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ProtobufPipelineResult result = pipeline.transform(srcOf(in), SRC_OFFSET, SRC_OFFSET + in.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);

        assertEquals(Status.COMPLETED, result.status());
        byte[] out = new byte[result.produced()];
        dst.getBytes(DST_OFFSET, out);
        assertEquals("{\"name\":\"neo\",\"id\":5}", new String(out, UTF_8));
    }

    private byte[] drainToCompletion(
        ProtobufPipeline pipeline,
        byte[] in,
        int dstCap)
    {
        MutableDirectBuffer src = srcOf(in);
        MutableDirectBuffer dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();
        ProtobufPipelineResult result;
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

    private static byte[] wire(
        Consumer<ProtobufWriter> body)
    {
        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
        ProtobufWriter writer = new ProtobufWriter().wrap(buffer, 0);
        body.accept(writer);
        byte[] bytes = new byte[writer.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static ProtobufSchema newSchema()
    {
        return Protobuf.schema()
            .message(ProtobufMessage.builder("P")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .field(ProtobufField.builder().number(2).name("id").type(ProtobufType.INT32).build())
                .build())
            .build();
    }
}
