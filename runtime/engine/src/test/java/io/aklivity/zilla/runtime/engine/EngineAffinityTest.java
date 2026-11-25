/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class EngineAffinityTest
{
    private static final int WORKERS = 64;

    @Test
    public void shouldParseNothingWhenAbsent()
    {
        assertTrue(EngineAffinity.parse(null, WORKERS).isEmpty());
        assertTrue(EngineAffinity.parse("", WORKERS).isEmpty());
        assertTrue(EngineAffinity.parse("  ", WORKERS).isEmpty());
    }

    @Test
    public void shouldParseSingleCore()
    {
        final List<EngineAffinity> affinities = EngineAffinity.parse("example.north_tcp_server=3", WORKERS);

        assertEquals(1, affinities.size());
        assertEquals("example", affinities.get(0).namespace);
        assertEquals("north_tcp_server", affinities.get(0).binding);
        assertEquals(0b1000L, affinities.get(0).mask);
    }

    @Test
    public void shouldParseCoreRange()
    {
        final List<EngineAffinity> affinities = EngineAffinity.parse("example.binding=16-31", WORKERS);

        assertEquals(0xffff_0000L, affinities.get(0).mask);
    }

    @Test
    public void shouldParseCoreListWithRanges()
    {
        final List<EngineAffinity> affinities = EngineAffinity.parse("example.binding=0,2,4-5", WORKERS);

        assertEquals(1, affinities.size());
        assertEquals(0b11_0101L, affinities.get(0).mask);
    }

    @Test
    public void shouldParseMultipleBindings()
    {
        final List<EngineAffinity> affinities =
            EngineAffinity.parse("example.north=16-31,example.south=0,1,2,other.east=7", WORKERS);

        assertEquals(3, affinities.size());
        assertEquals("north", affinities.get(0).binding);
        assertEquals(0xffff_0000L, affinities.get(0).mask);
        assertEquals("south", affinities.get(1).binding);
        assertEquals(0b111L, affinities.get(1).mask);
        assertEquals("other", affinities.get(2).namespace);
        assertEquals("east", affinities.get(2).binding);
        assertEquals(0b1000_0000L, affinities.get(2).mask);
    }

    @Test
    public void shouldSplitNamespaceFromBindingAtFirstDot()
    {
        final List<EngineAffinity> affinities = EngineAffinity.parse("example.north.tcp=1", WORKERS);

        assertEquals("example", affinities.get(0).namespace);
        assertEquals("north.tcp", affinities.get(0).binding);
    }

    @Test
    public void shouldIgnoreWhitespace()
    {
        final List<EngineAffinity> affinities = EngineAffinity.parse(" example.a = 0 - 1 , 3 , example.b = 2 ", WORKERS);

        assertEquals(2, affinities.size());
        assertEquals(0b1011L, affinities.get(0).mask);
        assertEquals(0b100L, affinities.get(1).mask);
    }

    @Test
    public void shouldParseHighestWorker()
    {
        assertEquals(1L << 63, EngineAffinity.parse("example.binding=63", WORKERS).get(0).mask);
    }

    @Test
    public void shouldRejectCoreBeyondWorkers()
    {
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=4", 4));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=0-4", 4));
    }

    @Test
    public void shouldRejectCoreListWithoutBinding()
    {
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("0-3", WORKERS));
    }

    @Test
    public void shouldRejectBindingWithoutNamespace()
    {
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("binding=0", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse(".binding=0", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.=0", WORKERS));
    }

    @Test
    public void shouldRejectBindingWithoutCores()
    {
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.a=,example.b=1", WORKERS));
    }

    @Test
    public void shouldRejectMalformedCores()
    {
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=a", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=-1", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=3-1", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=1-", WORKERS));
        assertThrows(IllegalArgumentException.class, () -> EngineAffinity.parse("example.binding=0,,1", WORKERS));
    }
}
