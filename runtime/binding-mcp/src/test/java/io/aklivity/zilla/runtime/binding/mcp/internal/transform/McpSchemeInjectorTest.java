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
package io.aklivity.zilla.runtime.binding.mcp.internal.transform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

public class McpSchemeInjectorTest
{
    @Test
    public void shouldArmOnArrayKeyThatFragmentsAcrossInputWindows()
    {
        // the arm key is longer than the feed window and fragments across STARVED windows before
        // onOuterKey's decline-until-complete resolves it; the injection it enables one level down must
        // still fire correctly once the key completes. Every outer key is forwarded regardless of match,
        // and this stage does not deliver verbatim bytes, so declining and forwarding the whole reassembled
        // key afterward is safe.
        String arrayKey = "x".repeat(40);
        Function<String, Map<String, List<String>>> rolesByTool =
            name -> "alpha".equals(name) ? Map.of("g1", List.of("r1")) : Map.of();
        String json = "{\"" + arrayKey + "\":[{\"name\":\"alpha\",\"x\":1}]}";
        assertEquals(
            "{\"" + arrayKey + "\":[{\"name\":\"alpha\",\"x\":1,\"securitySchemes\":" +
                "[{\"type\":\"oauth2\",\"scopes\":[\"r1\"]}]}]}",
            injectWindowed(arrayKey, rolesByTool, json, 12));
    }

    @Test
    public void shouldResolveNameKeyThatFragmentsAcrossInputWindows()
    {
        // "name" fragments across STARVED windows before onElementKey's decline-until-complete resolves it.
        // Values here are long enough that they never themselves fragment at this window, isolating the key
        // fragmentation this test targets from the separate (also fixed) name-value handling.
        Function<String, Map<String, List<String>>> rolesByTool =
            name -> "alphabetagamma".equals(name) ? Map.of("g1", List.of("r1")) : Map.of();
        String json = "{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1}]}";
        assertEquals(
            "{\"tools\":[{\"name\":\"alphabetagamma\",\"x\":1,\"securitySchemes\":" +
                "[{\"type\":\"oauth2\",\"scopes\":[\"r1\"]}]}]}",
            injectWindowed("tools", rolesByTool, json, 3));
    }

    @Test
    public void shouldResolveNameValueThatFragmentsAcrossInputWindows()
    {
        // the name VALUE itself (not just its key) is long enough to fragment across the feed window;
        // rolesByTool must not be invoked against a prefix -- a related bug in the same method fixed
        // alongside the key-fragmentation issue this class targets
        Function<String, Map<String, List<String>>> rolesByTool =
            name -> "alphabetagammadelta".equals(name) ? Map.of("g1", List.of("r1")) : Map.of();
        String json = "{\"tools\":[{\"name\":\"alphabetagammadelta\",\"x\":1}]}";
        assertEquals(
            "{\"tools\":[{\"name\":\"alphabetagammadelta\",\"x\":1,\"securitySchemes\":" +
                "[{\"type\":\"oauth2\",\"scopes\":[\"r1\"]}]}]}",
            injectWindowed("tools", rolesByTool, json, 6));
    }

    private static String injectWindowed(
        String arrayKey,
        Function<String, Map<String, List<String>>> rolesByTool,
        String input,
        int window)
    {
        McpSchemeInjector injector = new McpSchemeInjector(arrayKey, true, rolesByTool);

        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(injector)
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
