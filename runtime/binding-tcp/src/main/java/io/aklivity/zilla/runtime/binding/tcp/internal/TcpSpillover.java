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

import java.util.function.IntUnaryOperator;

final class TcpSpillover
{
    static final int NO_PEER = -1;
    static final int FULL = Integer.MAX_VALUE;

    private final int self;
    private final int tolerance;

    private long state;

    TcpSpillover(
        int self,
        int tolerance)
    {
        this.self = self;
        this.tolerance = tolerance;
        this.state = 0x9E3779B97F4A7C15L * (self + 1L);
    }

    int select(
        int count,
        int local,
        boolean full,
        IntUnaryOperator peerLoad)
    {
        int selected = NO_PEER;

        final int peers = count - 1;
        if (peers > 0)
        {
            final int first = nextInt(peers);
            int best = peer(first);
            int bestLoad = peerLoad.applyAsInt(best);

            if (peers > 1)
            {
                int second = nextInt(peers - 1);
                second = second >= first ? second + 1 : second;

                final int other = peer(second);
                final int otherLoad = peerLoad.applyAsInt(other);

                if (otherLoad < bestLoad)
                {
                    best = other;
                    bestLoad = otherLoad;
                }
            }

            if (bestLoad != FULL && (full || local > bestLoad + margin(bestLoad)))
            {
                selected = best;
            }
            else if (full)
            {
                selected = withCapacity(peers, peerLoad);
            }
        }

        return selected;
    }

    private int withCapacity(
        int peers,
        IntUnaryOperator peerLoad)
    {
        int selected = NO_PEER;

        final int start = nextInt(peers);
        for (int offset = 0; selected == NO_PEER && offset < peers; offset++)
        {
            final int candidate = peer((start + offset) % peers);

            if (peerLoad.applyAsInt(candidate) != FULL)
            {
                selected = candidate;
            }
        }

        return selected;
    }

    private int margin(
        int load)
    {
        // hash noise in a worker's connection count grows with the square root of the count
        return 1 << (((Integer.SIZE - Integer.numberOfLeadingZeros(load)) >> 1) + tolerance);
    }

    private int peer(
        int ordinal)
    {
        return ordinal >= self ? ordinal + 1 : ordinal;
    }

    private int nextInt(
        int bound)
    {
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;

        return (int) (((state >>> 32) * bound) >>> 32);
    }
}
