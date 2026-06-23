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
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

class JsonPipelineTransformTest
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
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "{\"a\":1}".getBytes(UTF_8);
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[DST_OFFSET + dstCap]);
        JsonPipelineResult result = pipeline.transform(srcOf(in), SRC_OFFSET, SRC_OFFSET + in.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);

        assertEquals(Status.COMPLETED, result.status());
        assertEquals(in.length, result.consumed());
        assertTrue(result.produced() <= dstCap);
        byte[] out = new byte[result.produced()];
        dst.getBytes(DST_OFFSET, out);
        assertEquals("{\"a\":1}", new String(out, UTF_8));
    }

    @Test
    void shouldForwardTrailingNewlineVerbatim()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "{\"a\":1}\n".getBytes(UTF_8);
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[DST_OFFSET + dstCap]);
        JsonPipelineResult result = pipeline.transform(srcOf(in), SRC_OFFSET, SRC_OFFSET + in.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);

        assertEquals(Status.COMPLETED, result.status());
        assertEquals(in.length, result.consumed());
        byte[] out = new byte[result.produced()];
        dst.getBytes(DST_OFFSET, out);
        assertEquals("{\"a\":1}\n", new String(out, UTF_8));
    }

    @Test
    void shouldSuspendOnBoundedOutputThenCompleteAcrossDrains()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createGenerator());
        pipeline.reset();

        // a long string value exercises the consumption-driven path; a destination smaller than the value
        // forces SUSPENDED, draining across calls
        String value = "x".repeat(40);
        byte[] in = ("{\"a\":\"" + value + "\"}").getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 16);

        assertEquals(new String(in, UTF_8), drained);
    }

    @Test
    void shouldNotOverrunDestinationOnLongKey()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createGenerator());
        pipeline.reset();

        // a key longer than the destination bound must fragment, not write past the bound
        String key = "k".repeat(40);
        byte[] in = ("{\"" + key + "\":1}").getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 16);

        assertEquals(new String(in, UTF_8), drained);
    }

    @Test
    void shouldNotOverrunDestinationOnLongKeyThroughProjector()
    {
        // a projector buffers the whole key and forwards it during the value's event; a bounded destination
        // must still fragment that buffered key without overrunning or re-emitting
        String key = "k".repeat(40);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/" + key)))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = ("{\"" + key + "\":1}").getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 16);

        assertEquals(new String(in, UTF_8), drained);
    }

    @Test
    void shouldStarveThenCompleteAcrossInputWindows()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] f1 = "{\"a\":1,".getBytes(UTF_8);
        byte[] f2 = "\"b\":2} ".getBytes(UTF_8);
        int dstCap = 64;
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();

        JsonPipelineResult first = pipeline.transform(srcOf(f1), SRC_OFFSET, SRC_OFFSET + f1.length, false,
            dst, DST_OFFSET, DST_OFFSET + dstCap);
        assertEquals(Status.STARVED, first.status());
        assertTrue(first.produced() <= dstCap);
        drainChunk(dst, first.produced(), drained);

        JsonPipelineResult second = pipeline.transform(srcOf(f2), SRC_OFFSET, SRC_OFFSET + f2.length, true,
            dst, DST_OFFSET, DST_OFFSET + dstCap);
        assertEquals(Status.COMPLETED, second.status());
        assertTrue(second.produced() <= dstCap);
        drainChunk(dst, second.produced(), drained);

        assertEquals("{\"a\":1,\"b\":2} ", drained.toString(UTF_8));
    }

    @Test
    void shouldReportIdentityForVerbatimPipeline()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser()).into(JsonEx.createGenerator());

        assertTrue(pipeline.identity());
    }

    @Test
    void shouldNotReportIdentityThroughProjector()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(JsonEx.projector(List.of("/a")))
            .into(JsonEx.createGenerator());

        assertFalse(pipeline.identity());
    }

    private String drainToCompletion(
        JsonPipeline pipeline,
        byte[] in,
        int dstCap)
    {
        MutableDirectBuffer src = srcOf(in);
        MutableDirectBuffer dst = new UnsafeBuffer(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();
        JsonPipelineResult result;
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
        return drained.toString(UTF_8);
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
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[SRC_OFFSET + bytes.length]);
        buffer.putBytes(SRC_OFFSET, bytes);
        return buffer;
    }
}
