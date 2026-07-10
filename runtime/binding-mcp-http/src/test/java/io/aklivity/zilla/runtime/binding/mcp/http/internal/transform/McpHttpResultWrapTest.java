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

import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

public class McpHttpResultWrapTest
{
    @Test
    public void shouldWrapArrayRoot()
    {
        String json = "[{\"id\":1,\"name\":\"Bramble\"}]";
        assertEquals("{\"result\":" + json + "}", wrapWindowed(json, json.length()));
    }

    @Test
    public void shouldWrapScalarRoot()
    {
        assertEquals("{\"result\":42}", wrapWindowed("42", 2));
    }

    @Test
    public void shouldNotWrapObjectRoot()
    {
        String json = "{\"id\":1,\"name\":\"Bramble\"}";
        assertEquals(json, wrapWindowed(json, json.length()));
    }

    @Test
    public void shouldNotWrapObjectRootThatFragmentsAcrossInputWindows()
    {
        String json = "{\"id\":1,\"name\":\"Bramble\",\"tag\":\"dog\",\"owner\":{\"id\":2,\"name\":\"Nibbles\"}}";
        assertEquals(json, wrapWindowed(json, 5));
    }

    @Test
    public void shouldWrapArrayRootThatFragmentsAcrossInputWindows()
    {
        String json = "[{\"id\":1,\"name\":\"Bramble\",\"tag\":\"dog\"},{\"id\":2,\"name\":\"Nibbles\"}]";
        assertEquals("{\"result\":" + json + "}", wrapWindowed(json, 5));
    }

    private static String wrapWindowed(
        String input,
        int window)
    {
        McpHttpResultWrap wrap = new McpHttpResultWrap();

        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(wrap)
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        pipeline.reset();

        byte[] msg = input.getBytes(UTF_8);
        int progress = 0;
        int limit = 0;
        Status status = Status.STARVED;
        int guard = 0;
        while (status == Status.STARVED && guard++ < 10_000)
        {
            limit = Math.min(limit + window, msg.length);
            boolean last = limit >= msg.length;
            status = pipeline.transform(new UnsafeBufferEx(msg), progress, limit, last);
            if (status == Status.STARVED)
            {
                progress = limit - pipeline.remaining();
            }
        }
        assertEquals(Status.COMPLETED, status);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
