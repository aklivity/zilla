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
package io.aklivity.zilla.runtime.binding.mcp.internal.transform;

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

public class McpSchemeExcluderTest
{
    @Test
    public void shouldDropSecuritySchemesOfMatchedArrayElement()
    {
        assertEquals("{\"tools\":[{\"other\":1}]}",
            exclude("tools", "{\"tools\":[{\"securitySchemes\":{\"a\":1},\"other\":1}]}", 1024));
    }

    @Test
    public void shouldArmOnArrayKeyThatFragmentsAcrossInputWindows()
    {
        // the arm key itself is longer than the feed window and fragments across STARVED windows before
        // onArmKey's incremental (zero-buffer) match completes; the drop decision it enables one level down
        // must still fire correctly once the key completes. window=12 is chosen to clear a floor unrelated
        // to this fix: JsonSource#skipValue() scans the dropped member's value in one shot with no ability
        // to starve mid-value, so the window in which the (possibly reassembled) key completes must still
        // have enough bytes left to also cover the whole dropped value -- the same pre-existing, orthogonal
        // window-vs-content-size constraint JsonProjectorTest documents for MAX_VALUE_SIZE.
        String arrayKey = "x".repeat(40);
        String json = "{\"" + arrayKey + "\":[{\"securitySchemes\":{\"a\":1},\"other\":1}]}";
        assertEquals("{\"" + arrayKey + "\":[{\"other\":1}]}", excludeWindowed(arrayKey, json, 12));
    }

    @Test
    public void shouldDropSecuritySchemesKeyThatFragmentsAcrossInputWindows()
    {
        // "securitySchemes" itself is declined across STARVED windows before onElementKey can compare it
        // complete; the member must still be dropped once the key finishes reassembling. window=9 clears the
        // same skipValue() floor described above.
        String json = "{\"tools\":[{\"securitySchemes\":{\"a\":1},\"other\":1}]}";
        assertEquals("{\"tools\":[{\"other\":1}]}", excludeWindowed("tools", json, 9));
    }

    @Test
    public void shouldForwardMismatchedElementKeyThatFragmentsAcrossInputWindows()
    {
        // an element key that is not securitySchemes but is long enough to fragment across the feed window
        // must still be forwarded (with its full, correct content) rather than mistaken for a match or
        // dropped outright
        String key = "x".repeat(40);
        String json = "{\"tools\":[{\"" + key + "\":1,\"other\":2}]}";
        assertEquals("{\"tools\":[{\"" + key + "\":1,\"other\":2}]}", excludeWindowed("tools", json, 8));
    }

    private static String exclude(
        String arrayKey,
        String input,
        int bufferCapacity)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[bufferCapacity]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpSchemeExcluder(arrayKey))
            .into(JsonEx.createSink(gen, Map.of(JsonSink.DELIVERY, JsonSink.Delivery.STRUCTURED)));
        byte[] bytes = input.getBytes(UTF_8);
        pipeline.reset();
        pipeline.transform(new UnsafeBufferEx(bytes), 0, bytes.length);
        byte[] out = new byte[gen.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }

    // Drives the exclusion through fixed-size input windows, carrying the unconsumed tail
    // (pipeline.remaining()) across STARVED feeds the way a real caller does, so an over-window key
    // fragments and reassembles before this stage matches or forwards it.
    private static String excludeWindowed(
        String arrayKey,
        String input,
        int window)
    {
        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(new McpSchemeExcluder(arrayKey))
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
