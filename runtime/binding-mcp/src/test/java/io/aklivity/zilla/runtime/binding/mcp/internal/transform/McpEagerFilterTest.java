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
import java.util.function.Predicate;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

public class McpEagerFilterTest
{
    @Test
    public void shouldArmOnArrayKeyThatFragmentsAcrossInputWindows()
    {
        // the arm key is longer than the feed window and fragments across STARVED windows before
        // onOuterKey's decline-until-complete resolves it; the eager/cold split it enables one level down
        // must still fire correctly once the key completes. Every outer key is forwarded regardless of
        // match, and this stage does not deliver verbatim bytes, so declining and forwarding the whole
        // reassembled key afterward is safe.
        String arrayKey = "x".repeat(40);
        Predicate<CharSequence> eager = name -> "alpha".contentEquals(name);
        String json = "{\"" + arrayKey + "\":[{\"name\":\"alpha\",\"x\":1},{\"name\":\"beta\",\"x\":2}]}";
        assertEquals(
            "{\"" + arrayKey + "\":[{\"name\":\"alpha\",\"x\":1},{\"name\":\"beta\",\"defer_loading\":true,\"x\":2}]}",
            filterWindowed(arrayKey, eager, false, json, 12));
    }

    @Test
    public void shouldResolveNameKeyThatFragmentsAcrossInputWindows()
    {
        // "name" (the item's own key, always swallowed and replaced by a synthetic constant regardless of
        // outcome) fragments across STARVED windows before onNameKey's decline-until-complete resolves it.
        // Values here are long enough that they never themselves fragment at this window, isolating the key
        // fragmentation this test targets from McpEagerFilter's separate (also fixed) name-value handling.
        Predicate<CharSequence> eager = name -> "alphabetagamma".contentEquals(name);
        String json = "{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1},{\"name\":\"deltaepsilonzeta\",\"x\":2}]}";
        assertEquals(
            "{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1}," +
                "{\"name\":\"deltaepsilonzeta\",\"defer_loading\":true,\"x\":2}]}",
            filterWindowed("tools", eager, false, json, 3));
    }

    @Test
    public void shouldOmitColdItemWhoseNameKeyFragments()
    {
        Predicate<CharSequence> eager = name -> "alphabetagamma".contentEquals(name);
        String json = "{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1},{\"name\":\"deltaepsilonzeta\",\"x\":2}]}";
        assertEquals("{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1}]}",
            filterWindowed("tools", eager, true, json, 3));
    }

    @Test
    public void shouldResolveNameValueThatFragmentsAcrossInputWindows()
    {
        // the name VALUE itself (not just its key) is long enough to fragment across the feed window;
        // resolveItem() must not evaluate eager.test() against a prefix -- a related bug in the same method
        // fixed alongside the key-fragmentation issue this class targets
        Predicate<CharSequence> eager = name -> "alphabetagammadelta".contentEquals(name);
        String json = "{\"tools\":[{\"name\":\"alphabetagammadelta\",\"x\":1}]}";
        assertEquals("{\"tools\":[{\"name\":\"alphabetagammadelta\",\"x\":1}]}",
            filterWindowed("tools", eager, false, json, 6));
    }

    // Drives the filter through fixed-size input windows, carrying the unconsumed tail
    // (pipeline.remaining()) across STARVED feeds the way a real caller does, so an over-window key
    // fragments and reassembles before this stage matches or forwards it.
    private static String filterWindowed(
        String arrayKey,
        Predicate<CharSequence> eager,
        boolean omitCold,
        String input,
        int window)
    {
        McpEagerFilter filter = new McpEagerFilter();
        filter.init(arrayKey, eager, omitCold);

        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(filter)
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
