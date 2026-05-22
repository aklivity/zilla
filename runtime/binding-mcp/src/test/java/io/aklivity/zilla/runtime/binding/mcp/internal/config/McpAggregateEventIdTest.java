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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class McpAggregateEventIdTest
{
    private final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);

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
        int length = McpAggregateEventId.encode(new String[] {"E"}, new String[] {"12"}, buffer, 0);

        assertEquals("E=12", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldEncodeMultiplePairsInPrefixOrder()
    {
        int length = McpAggregateEventId.encode(new String[] {"E", "m"}, new String[] {"12", "ab9"}, buffer, 0);

        assertEquals("E=12;m=ab9", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldEncodeSkipNullIds()
    {
        int length = McpAggregateEventId.encode(new String[] {"E", "m"}, new String[] {null, "ab9"}, buffer, 0);

        assertEquals("m=ab9", buffer.getStringWithoutLengthUtf8(0, length));
    }

    @Test
    public void shouldEncodeReturnsNegativeWhenAllIdsNull()
    {
        int length = McpAggregateEventId.encode(new String[] {"E", "m"}, new String[] {null, null}, buffer, 0);

        assertEquals(-1, length);
    }

    @Test
    public void shouldEncodeAtNonZeroOffset()
    {
        buffer.putStringWithoutLengthUtf8(0, "preamble:");
        int offset = "preamble:".length();
        int length = McpAggregateEventId.encode(new String[] {"E", "m"}, new String[] {"12", "ab9"}, buffer, offset);

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

        String[] prefixes = input.keySet().stream().sorted().toArray(String[]::new);
        String[] ids = Arrays.stream(prefixes).map(input::get).toArray(String[]::new);
        int length = McpAggregateEventId.encode(prefixes, ids, buffer, 0);
        String aggregate = buffer.getStringWithoutLengthUtf8(0, length);

        Map<String, String> decoded = new LinkedHashMap<>();
        McpAggregateEventId.decode(aggregate, decoded::put);

        assertEquals(input, decoded);
    }

    @Test
    public void shouldEncodeWithCanonicalSortOrder()
    {
        String[] prefixes = {"E", "m", "z"};
        Arrays.sort(prefixes, Comparator.naturalOrder());
        int length = McpAggregateEventId.encode(prefixes, new String[] {"1", "2", "3"}, buffer, 0);

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
