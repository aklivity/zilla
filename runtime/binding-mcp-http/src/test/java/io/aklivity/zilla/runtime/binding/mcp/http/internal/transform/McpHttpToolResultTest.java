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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;

public class McpHttpToolResultTest
{
    private static final int SRC_OFFSET = 5;
    private static final int DST_OFFSET = 7;
    private static final int GUARD = 10_000;

    @Test
    public void shouldWrapStructuredContentWithSummary()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpToolResult(() -> "Created pull request #42"))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "{\"number\":42}".getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 256);

        assertEquals(envelope("{\"number\":42}", "Created pull request #42"), drained);
    }

    @Test
    public void shouldSuspendAndResumeAcrossManySmallWindowsForALargeSummary()
    {
        // a summary far larger than the tiny destination bound: forces the trailer's own injected
        // VALUE_STRING step to suspend and resume repeatedly, proving the resumable synthetic source
        // shrinks correctly across many drain cycles rather than overflowing or re-presenting from the
        // start each time
        String summary = "x".repeat(5000);
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpToolResult(() -> summary))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "{\"a\":1,\"b\":[1,2,3]}".getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 16);

        assertEquals(envelope("{\"a\":1,\"b\":[1,2,3]}", summary), drained);
    }

    @Test
    public void shouldWrapArrayRootStructuredContent()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpToolResult(() -> "done"))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "[1,2,3]".getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 256);

        assertEquals(envelope("[1,2,3]", "done"), drained);
    }

    @Test
    public void shouldWrapBareScalarRootStructuredContent()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpToolResult(() -> "done"))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "42".getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 256);

        assertEquals(envelope("42", "done"), drained);
    }

    @Test
    public void shouldWrapWithEmptySummaryWhenNoneConfigured()
    {
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpToolResult(() -> null))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "{}".getBytes(UTF_8);
        String drained = drainToCompletion(pipeline, in, 256);

        assertEquals(envelope("{}", ""), drained);
    }

    @Test
    public void shouldResolveSummaryOnlyOnce()
    {
        int[] calls = new int[1];
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpHttpToolResult(() ->
            {
                calls[0]++;
                return "x".repeat(500);
            }))
            .into(JsonEx.createGenerator());
        pipeline.reset();

        byte[] in = "{\"a\":1}".getBytes(UTF_8);
        drainToCompletion(pipeline, in, 16);

        assertEquals(1, calls[0]);
    }

    private static String envelope(
        String structuredContent,
        String summary)
    {
        return "{\"structuredContent\":" + structuredContent +
            ",\"content\":[{\"type\":\"text\",\"text\":\"" + summary + "\"}],\"isError\":false}";
    }

    private String drainToCompletion(
        JsonPipeline pipeline,
        byte[] in,
        int dstCap)
    {
        MutableDirectBufferEx src = srcOf(in);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[DST_OFFSET + dstCap]);
        ByteArrayOutputStream drained = new ByteArrayOutputStream();
        JsonPipelineResult result;
        int guard = 0;
        do
        {
            result = pipeline.transform(src, SRC_OFFSET, SRC_OFFSET + in.length, true, dst, DST_OFFSET, DST_OFFSET + dstCap);
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
        MutableDirectBufferEx dst,
        int produced,
        ByteArrayOutputStream sink)
    {
        byte[] chunk = new byte[produced];
        dst.getBytes(DST_OFFSET, chunk);
        sink.writeBytes(chunk);
    }

    private static MutableDirectBufferEx srcOf(
        byte[] bytes)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[SRC_OFFSET + bytes.length]);
        buffer.putBytes(SRC_OFFSET, bytes);
        return buffer;
    }
}
