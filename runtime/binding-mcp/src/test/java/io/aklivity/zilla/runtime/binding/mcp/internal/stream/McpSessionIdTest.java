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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpSessionId.newSessionId;
import static java.lang.Math.floorMod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.util.function.LongIntPredicate;

public class McpSessionIdTest
{
    private static final long ROUTED_ID = 0x0000_0001_0000_0009L;

    // mirror EngineWorker.isLocalIndex for an uncapped binding (mask == all workers)
    private static LongIntPredicate alignsTo(
        int workers,
        int localIndex)
    {
        return (routedId, hash) -> floorMod(hash, workers) == localIndex;
    }

    private static Supplier<String> randomSessionIds(
        long seed)
    {
        final Random random = new Random(seed);
        return () -> new UUID(random.nextLong(), random.nextLong()).toString();
    }

    @Test
    public void shouldAlignToLocalWorkerForSingleWorker()
    {
        final Supplier<String> supply = randomSessionIds(11);
        for (int session = 0; session < 1000; session++)
        {
            final String sessionId = newSessionId(ROUTED_ID, 2, supply, alignsTo(1, 0));
            assertNotNull(sessionId);
            assertEquals(0, floorMod(sessionId.hashCode(), 1));
        }
    }

    @Test
    public void shouldReturnNullWhenAttemptsExhausted()
    {
        // the legacy workers * 2 cap exhausts ~((N-1)/N)^(2N) ~= 13.5% of generations
        final int workers = 8;
        final int attempts = workers * 2;
        final Supplier<String> supply = randomSessionIds(7);

        boolean nulled = false;
        for (int session = 0; session < 5000 && !nulled; session++)
        {
            nulled = newSessionId(ROUTED_ID, attempts, supply, alignsTo(workers, 3)) == null;
        }

        assertTrue("expected reject-sampling to exhaust its attempts at the legacy cap", nulled);
    }

    @Test
    public void shouldAlignToLocalWorkerAcrossWorkers()
    {
        for (int workers = 1; workers <= 64; workers <<= 1)
        {
            final int attempts = workers * 64;
            for (int localIndex = 0; localIndex < workers; localIndex++)
            {
                final Supplier<String> supply = randomSessionIds(localIndex * 131L + workers);
                final LongIntPredicate isLocalIndex = alignsTo(workers, localIndex);
                for (int session = 0; session < 500; session++)
                {
                    final String sessionId = newSessionId(ROUTED_ID, attempts, supply, isLocalIndex);
                    assertNotNull("null session id for workers=" + workers + " localIndex=" + localIndex, sessionId);
                    assertEquals(localIndex, floorMod(sessionId.hashCode(), workers));
                }
            }
        }
    }

    @Test
    public void shouldReturnNullWhenNeverAligned()
    {
        assertNull(newSessionId(ROUTED_ID, 64, randomSessionIds(3), (routedId, hash) -> false));
    }
}
