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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpSpillover.FULL;
import static io.aklivity.zilla.runtime.binding.tcp.internal.TcpSpillover.NO_PEER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TcpSpilloverTest
{
    private static final int TOLERANCE = 3;
    private static final long ALL = -1L;

    @Test
    public void shouldSelectNoPeerWhenAlone()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(1, ALL, 100, true, peer -> 0));
    }

    @Test
    public void shouldSelectNoPeerWhenBalanced()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 16; attempt++)
        {
            assertEquals(NO_PEER, spillover.select(4, ALL, 10, false, peer -> 10));
        }
    }

    @Test
    public void shouldSelectNoPeerWhenWithinMargin()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 16; attempt++)
        {
            assertEquals(NO_PEER, spillover.select(4, ALL, 42, false, peer -> 10));
        }
    }

    @Test
    public void shouldSelectPeerWhenBeyondMargin()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 16; attempt++)
        {
            assertTrue(spillover.select(4, ALL, 43, false, peer -> 10) > 0);
        }
    }

    @Test
    public void shouldSelectEmptyPeerOnlyWhenBeyondMargin()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(2, ALL, 8, false, peer -> 0));
        assertEquals(1, spillover.select(2, ALL, 9, false, peer -> 0));
    }

    @Test
    public void shouldTightenMarginAsLoadGrows()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(2, ALL, 1256, false, peer -> 1000));
        assertEquals(1, spillover.select(2, ALL, 1257, false, peer -> 1000));

        assertEquals(NO_PEER, spillover.select(2, ALL, 1_008_192, false, peer -> 1_000_000));
        assertEquals(1, spillover.select(2, ALL, 1_008_193, false, peer -> 1_000_000));
    }

    @Test
    public void shouldWidenMarginWithGreaterTolerance()
    {
        TcpSpillover strict = new TcpSpillover(0, 1);
        TcpSpillover tolerant = new TcpSpillover(0, 5);

        assertEquals(1, strict.select(2, ALL, 1065, false, peer -> 1000));
        assertEquals(NO_PEER, tolerant.select(2, ALL, 1065, false, peer -> 1000));
    }

    @Test
    public void shouldSelectPeerWithCapacityWhenFullRegardlessOfMargin()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 16; attempt++)
        {
            assertTrue(spillover.select(4, ALL, 1000, true, peer -> 999) > 0);
        }
    }

    @Test
    public void shouldFindOnlyPeerWithCapacityWhenFull()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 32; attempt++)
        {
            assertEquals(5, spillover.select(8, ALL, 1000, true, peer -> peer == 5 ? 900 : FULL));
        }
    }

    @Test
    public void shouldSelectNoPeerWhenFullAndNoPeerHasCapacity()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(8, ALL, 1000, true, peer -> FULL));
    }

    @Test
    public void shouldSelectNoPeerWhenNotFullAndNoPeerHasCapacity()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(8, ALL, 1000, false, peer -> FULL));
    }

    @Test
    public void shouldNeverSelectSelf()
    {
        TcpSpillover spillover = new TcpSpillover(2, TOLERANCE);

        for (int attempt = 0; attempt < 256; attempt++)
        {
            final int selected = spillover.select(5, ALL, 100, false, peer -> 10);

            assertTrue(selected >= 0 && selected < 5 && selected != 2);
        }
    }

    @Test
    public void shouldSelectLessLoadedOfTwoPeers()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 32; attempt++)
        {
            assertEquals(1, spillover.select(3, ALL, 100, false, peer -> peer == 1 ? 5 : 9));
        }
    }

    @Test
    public void shouldSkipFullPeerWhenSamplingTwo()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 32; attempt++)
        {
            assertEquals(2, spillover.select(3, ALL, 100, false, peer -> peer == 1 ? FULL : 9));
        }
    }

    @Test
    public void shouldSpreadAcrossEquallyLoadedPeers()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);
        int[] selections = new int[5];

        for (int attempt = 0; attempt < 4000; attempt++)
        {
            selections[spillover.select(5, ALL, 100, false, peer -> 10)]++;
        }

        assertEquals(0, selections[0]);
        for (int peer = 1; peer < 5; peer++)
        {
            assertTrue("peer " + peer + " selected " + selections[peer], selections[peer] > 800);
        }
    }

    @Test
    public void shouldPreferLeastLoadedPeersWhenSpreading()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);
        int[] selections = new int[5];

        for (int attempt = 0; attempt < 4000; attempt++)
        {
            selections[spillover.select(5, ALL, 1000, false, peer -> peer * 100)]++;
        }

        assertTrue(selections[1] > selections[2]);
        assertTrue(selections[2] > selections[3]);
        assertTrue(selections[3] > selections[4]);
    }

    @Test
    public void shouldSelectOnlyPeersInMask()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 64; attempt++)
        {
            final int selected = spillover.select(8, 0b0110_0001L, 1000, false, peer -> 10);

            assertTrue("selected " + selected, selected == 5 || selected == 6);
        }
    }

    @Test
    public void shouldSelectNoPeerWhenNoPeerInMask()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(8, 0b0000_0001L, 1000, true, peer -> 10));
    }

    @Test
    public void shouldSelectPeerInMaskWhenSelfNotInMaskRegardlessOfLoad()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 64; attempt++)
        {
            final int selected = spillover.select(8, 0b1100_0000L, 0, false, peer -> 1000);

            assertTrue("selected " + selected, selected == 6 || selected == 7);
        }
    }

    @Test
    public void shouldFindOnlyPeerWithCapacityInMaskWhenSelfNotInMask()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 32; attempt++)
        {
            assertEquals(7, spillover.select(8, 0b1100_0000L, 0, false, peer -> peer == 7 ? 900 : FULL));
        }
    }

    @Test
    public void shouldSelectNoPeerWhenSelfNotInMaskAndNoPeerInMaskHasCapacity()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        assertEquals(NO_PEER, spillover.select(8, 0b1100_0000L, 0, false, peer -> FULL));
    }

    @Test
    public void shouldIgnoreMaskBitsBeyondPeerCount()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 64; attempt++)
        {
            assertEquals(1, spillover.select(2, 0b1111_1110L, 100, false, peer -> 0));
        }
    }

    @Test
    public void shouldSelectAcrossAllSixtyFourWorkers()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 64; attempt++)
        {
            assertEquals(63, spillover.select(64, 1L << 63, 0, false, peer -> 0));
        }
    }

    @Test
    public void shouldKeepConnectionWhenSelfInMaskAndBalanced()
    {
        TcpSpillover spillover = new TcpSpillover(0, TOLERANCE);

        for (int attempt = 0; attempt < 16; attempt++)
        {
            assertEquals(NO_PEER, spillover.select(8, 0b0110_0001L, 10, false, peer -> 10));
        }
    }
}
