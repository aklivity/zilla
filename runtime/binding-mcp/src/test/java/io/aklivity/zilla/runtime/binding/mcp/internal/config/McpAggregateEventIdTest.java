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
package io.aklivity.zilla.runtime.binding.mcp.internal.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.agrona.collections.Long2ObjectHashMap;
import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class McpAggregateEventIdTest
{
    private final MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    public void shouldComputeSinglePrefixWhenSingleToolkit()
    {
        Map<String, String> prefixes = McpAggregateEventId.computePrefixes(List.of("bluesky"));

        assertEquals(1, prefixes.size());
        assertEquals(1, prefixes.get("bluesky").length());
    }

    @Test
    public void shouldComputeUniquePrefixesForTwoToolkits()
    {
        Map<String, String> prefixes = McpAggregateEventId.computePrefixes(List.of("bluesky", "quartz"));

        assertEquals(2, prefixes.size());
        assertNotEquals(prefixes.get("bluesky"), prefixes.get("quartz"));

        int length = prefixes.get("bluesky").length();
        assertTrue(length >= 1 && length <= McpAggregateEventId.MAX_PREFIX_LENGTH);
        assertEquals(length, prefixes.get("quartz").length());
    }

    @Test
    public void shouldComputeUniquePrefixesForManyToolkits()
    {
        List<String> toolkits = List.of("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta");

        Map<String, String> prefixes = McpAggregateEventId.computePrefixes(toolkits);

        assertEquals(toolkits.size(), prefixes.size());

        Set<String> distinct = new HashSet<>(prefixes.values());
        assertEquals("prefixes must be unique across toolkits", toolkits.size(), distinct.size());
    }

    @Test
    public void shouldComputeEmptyWhenNoToolkits()
    {
        assertTrue(McpAggregateEventId.computePrefixes(List.of()).isEmpty());
        assertTrue(McpAggregateEventId.computePrefixes(null).isEmpty());
    }

    @Test
    public void shouldDeduplicateRepeatedToolkits()
    {
        Map<String, String> prefixes = McpAggregateEventId.computePrefixes(List.of("bluesky", "bluesky"));

        assertEquals(1, prefixes.size());
    }

    @Test
    public void shouldEncodeSinglePair()
    {
        McpAggregateRoute[] routes = { new McpAggregateRoute("E", 1L) };
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        ids.put(1L, "12");
        int length = McpAggregateEventId.encode(routes, ids, buffer, 0);

        assertEquals("E=12", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldEncodeMultiplePairsInPrefixOrder()
    {
        McpAggregateRoute[] routes = { new McpAggregateRoute("E", 1L), new McpAggregateRoute("m", 2L) };
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        ids.put(1L, "12");
        ids.put(2L, "ab9");
        int length = McpAggregateEventId.encode(routes, ids, buffer, 0);

        assertEquals("E=12;m=ab9", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldEncodeSkipNullIds()
    {
        McpAggregateRoute[] routes = { new McpAggregateRoute("E", 1L), new McpAggregateRoute("m", 2L) };
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        ids.put(2L, "ab9");
        int length = McpAggregateEventId.encode(routes, ids, buffer, 0);

        assertEquals("m=ab9", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldEncodeReturnsNegativeWhenAllIdsNull()
    {
        McpAggregateRoute[] routes = { new McpAggregateRoute("E", 1L), new McpAggregateRoute("m", 2L) };
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        int length = McpAggregateEventId.encode(routes, ids, buffer, 0);

        assertEquals(-1, length);
    }

    @Test
    public void shouldEncodeAtNonZeroOffset()
    {
        buffer.putStringWithoutLengthUtf8(0, "preamble:");
        int offset = "preamble:".length();
        McpAggregateRoute[] routes = { new McpAggregateRoute("E", 1L), new McpAggregateRoute("m", 2L) };
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        ids.put(1L, "12");
        ids.put(2L, "ab9");
        int length = McpAggregateEventId.encode(routes, ids, buffer, offset);

        assertEquals("E=12;m=ab9", buffer.getStringWithoutLengthUtf8(offset, length));
    }

    @Test
    public void shouldDecodeSinglePair()
    {
        Map<String, String> decoded = new LinkedHashMap<>();
        McpAggregateEventId.decode("E=12", decoded::put);

        assertEquals(1, decoded.size());
        assertEquals("12", decoded.get("E"));
    }

    @Test
    public void shouldDecodeMultiplePairs()
    {
        Map<String, String> decoded = new LinkedHashMap<>();
        McpAggregateEventId.decode("E=12;m=ab9", decoded::put);

        assertEquals(2, decoded.size());
        assertEquals("12", decoded.get("E"));
        assertEquals("ab9", decoded.get("m"));
    }

    @Test
    public void shouldDecodeNothingFromNullOrEmpty()
    {
        Map<String, String> decoded = new LinkedHashMap<>();
        McpAggregateEventId.decode(null, decoded::put);
        McpAggregateEventId.decode("", decoded::put);

        assertTrue(decoded.isEmpty());
    }

    @Test
    public void shouldDecodeIgnoresMalformedPair()
    {
        Map<String, String> decoded = new LinkedHashMap<>();
        McpAggregateEventId.decode("noequals;E=12", decoded::put);

        assertEquals(1, decoded.size());
        assertEquals("12", decoded.get("E"));
    }

    @Test
    public void shouldRoundTripEncodeDecode()
    {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("p1", "100");
        input.put("p2", "200");
        input.put("p3", "abc");

        McpAggregateRoute[] routes = new McpAggregateRoute[input.size()];
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        int index = 0;
        for (String prefix : input.keySet().stream().sorted().toList())
        {
            final long routedId = 100L + index;
            routes[index++] = new McpAggregateRoute(prefix, routedId);
            ids.put(routedId, input.get(prefix));
        }
        int length = McpAggregateEventId.encode(routes, ids, buffer, 0);
        String aggregate = buffer.getStringWithoutLengthUtf8(0, length);

        Map<String, String> decoded = new LinkedHashMap<>();
        McpAggregateEventId.decode(aggregate, decoded::put);

        assertEquals(input, decoded);
    }

    @Test
    public void shouldEncodeWithCanonicalSortOrder()
    {
        McpAggregateRoute[] routes =
        {
            new McpAggregateRoute("E", 1L),
            new McpAggregateRoute("m", 2L),
            new McpAggregateRoute("z", 3L)
        };
        Arrays.sort(routes, Comparator.comparing(McpAggregateRoute::prefix));
        Long2ObjectHashMap<String> ids = new Long2ObjectHashMap<>();
        ids.put(1L, "1");
        ids.put(2L, "2");
        ids.put(3L, "3");
        int length = McpAggregateEventId.encode(routes, ids, buffer, 0);

        assertEquals("E=1;m=2;z=3", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldDecodeNothingForUnknownPrefixGracefully()
    {
        Map<String, String> known = Map.of("E", "route-1");
        Map<String, String> resolved = new LinkedHashMap<>();
        McpAggregateEventId.decode("E=12;X=99;m=ab9", (prefix, id) ->
        {
            if (known.containsKey(prefix))
            {
                resolved.put(prefix, id);
            }
        });

        assertEquals(1, resolved.size());
        assertEquals("12", resolved.get("E"));
    }
}
