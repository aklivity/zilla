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
package io.aklivity.zilla.runtime.binding.mcp.http.internal.transform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;

public class McpHttpArgumentsTest
{
    @Test
    public void shouldForwardAllArgumentsWhenNoneExcluded()
    {
        String input = "{\"name\":\"tool\",\"arguments\":{\"a\":\"1\",\"b\":\"2\"}}";

        Map<String, String> captured = new LinkedHashMap<>();
        String output = reroot(input, List.of(), captured);

        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", output);
        assertEquals(Map.of("a", "1", "b", "2"), captured);
    }

    @Test
    public void shouldWithholdExcludedTopLevelArgumentFromOutputButStillCaptureIt()
    {
        String input = "{\"name\":\"update_connector_config\",\"arguments\":" +
            "{\"connector\":\"file-source-demo\",\"connector.class\":\"FileStreamSource\",\"topic\":\"connect-demo\"}}";

        Map<String, String> captured = new LinkedHashMap<>();
        String output = reroot(input, List.of("connector"), captured);

        assertEquals("{\"connector.class\":\"FileStreamSource\",\"topic\":\"connect-demo\"}", output);
        assertEquals(Map.of(
            "connector", "file-source-demo",
            "connector.class", "FileStreamSource",
            "topic", "connect-demo"), captured);
    }

    @Test
    public void shouldWithholdExcludedNestedObjectValueEntirely()
    {
        String input = "{\"name\":\"tool\",\"arguments\":" +
            "{\"connector\":{\"nested\":\"x\"},\"topic\":\"connect-demo\"}}";

        Map<String, String> captured = new LinkedHashMap<>();
        String output = reroot(input, List.of("connector"), captured);

        assertEquals("{\"topic\":\"connect-demo\"}", output);
        assertFalse(captured.containsKey("connector"));
    }

    @Test
    public void shouldWithholdOnlyExcludedKeyAmongMultipleArguments()
    {
        String input = "{\"name\":\"validate_connector_config\",\"arguments\":" +
            "{\"pluginName\":\"FileStreamSource\",\"connector.class\":\"FileStreamSource\"," +
            "\"file\":\"/tmp/kc-source.txt\",\"topic\":\"connect-demo\",\"name\":\"file-source-demo\"}}";

        Map<String, String> captured = new LinkedHashMap<>();
        String output = reroot(input, List.of("pluginName"), captured);

        assertEquals("{\"connector.class\":\"FileStreamSource\",\"file\":\"/tmp/kc-source.txt\"," +
            "\"topic\":\"connect-demo\",\"name\":\"file-source-demo\"}", output);
        assertEquals("FileStreamSource", captured.get("pluginName"));
    }

    @Test
    public void shouldWithholdExcludedTopLevelArgumentWhenFragmentedAcrossInputWindows()
    {
        String input = "{\"name\":\"create_pr\",\"arguments\":" +
            "{\"owner\":\"acme\",\"repo\":\"widget\",\"title\":\"Add feature\"," +
            "\"pr\":{\"branch\":\"feature\",\"target\":\"main\"}}}";

        for (int window = 1; window <= input.length(); window++)
        {
            Map<String, String> captured = new LinkedHashMap<>();
            String output = rerootWindowed(input, List.of("owner", "repo"), captured, window);

            assertEquals("window=" + window,
                "{\"title\":\"Add feature\",\"pr\":{\"branch\":\"feature\",\"target\":\"main\"}}", output);
            assertEquals("window=" + window, "acme", captured.get("owner"));
            assertEquals("window=" + window, "widget", captured.get("repo"));
        }
    }

    private static String reroot(
        String input,
        List<String> excludedKeys,
        Map<String, String> captured)
    {
        return rerootWindowed(input, excludedKeys, captured, input.length());
    }

    private static String rerootWindowed(
        String input,
        List<String> excludedKeys,
        Map<String, String> captured,
        int window)
    {
        McpHttpArguments transform = new McpHttpArguments(captured, excludedKeys);

        JsonGeneratorEx gen = JsonEx.createGenerator();
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[4096]);
        gen.wrap(buffer, 0, buffer.capacity());
        JsonPipeline pipeline = JsonEx.stream(JsonEx.createParser())
            .transform(transform)
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
