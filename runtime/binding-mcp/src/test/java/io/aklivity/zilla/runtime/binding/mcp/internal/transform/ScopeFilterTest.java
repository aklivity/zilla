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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipelineResult;

public class ScopeFilterTest
{
    @Test
    public void shouldFilterToolsByScope()
    {
        String json = "{\"tools\":[" +
            "{\"name\":\"list_repos\",\"description\":\"List repos\"," +
            "\"securitySchemes\":[{\"type\":\"oauth2\"," +
            "\"scopes\":[\"read\"]}]}," +
            "{\"name\":\"delete_repo\",\"description\":\"Delete repo\"," +
            "\"securitySchemes\":[{\"type\":\"oauth2\"," +
            "\"scopes\":[\"admin\"]}]}," +
            "{\"name\":\"get_status\",\"description\":\"Get status\"}" +
            "]}";

        Map<CharSequence, List<String>> scopesByName = new TreeMap<>(CharSequence::compare);
        scopesByName.put("list_repos", List.of("read"));
        scopesByName.put("delete_repo", List.of("admin"));

        McpScopeFilter filter = new McpScopeFilter(
            "tools",
            scopesByName,
            (name, scopes) ->
                List.of("read", "write").containsAll(scopes));

        String out = applyFilter(filter, json);

        assertTrue("Should include list_repos", out.contains("list_repos"));
        assertFalse("Should exclude delete_repo", out.contains("delete_repo"));
        assertTrue("Should include get_status", out.contains("get_status"));
    }

    @Test
    public void shouldPassThroughWhenNoScopesConfigured()
    {
        String json = "{\"tools\":[" +
            "{\"name\":\"list_repos\",\"description\":\"List repos\"}" +
            "]}";

        Map<CharSequence, List<String>> scopesByName = new TreeMap<>(CharSequence::compare);

        McpScopeFilter filter = new McpScopeFilter(
            "tools",
            scopesByName,
            (name, scopes) -> true);

        String out = applyFilter(filter, json);

        assertTrue("Should include list_repos", out.contains("list_repos"));
    }

    @Test
    public void shouldDropAllWhenNoneAdmitted()
    {
        String json = "{\"tools\":[" +
            "{\"name\":\"list_repos\",\"description\":\"List repos\"}" +
            "]}";

        Map<CharSequence, List<String>> scopesByName = new TreeMap<>(CharSequence::compare);
        scopesByName.put("list_repos", List.of("admin"));

        McpScopeFilter filter = new McpScopeFilter(
            "tools",
            scopesByName,
            (name, scopes) -> false);

        String out = applyFilter(filter, json);

        assertFalse("Should exclude list_repos", out.contains("list_repos"));
        assertTrue("Should have empty tools array", out.contains("\"tools\":[]"));
    }

    private String applyFilter(
        McpScopeFilter filter,
        String json)
    {
        JsonParserEx parser = JsonEx.createParser();
        JsonGeneratorEx generator = JsonEx.createGenerator();
        JsonPipeline pipeline = JsonEx.stream(parser)
            .transform(filter)
            .into(generator);

        byte[] src = json.getBytes(StandardCharsets.UTF_8);
        DirectBufferEx srcBuf = new UnsafeBufferEx(src);
        MutableDirectBufferEx dstBuf =
            new UnsafeBufferEx(new byte[src.length + 16]);

        pipeline.reset();
        JsonPipelineResult result = pipeline.transform(
            srcBuf, 0, src.length, true,
            dstBuf, 0, dstBuf.capacity());
        int produced = result.produced();

        while (result.status() == JsonPipeline.Status.SUSPENDED)
        {
            result = pipeline.transform(
                srcBuf, 0, src.length, true,
                dstBuf, produced, dstBuf.capacity());
            produced += result.produced();
        }

        byte[] output = new byte[produced];
        dstBuf.getBytes(0, output);
        return new String(output, StandardCharsets.UTF_8);
    }
}
