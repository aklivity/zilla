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
package io.aklivity.zilla.runtime.binding.mcp.internal.stream;

import static io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpSessionId.extractAffinity;
import static io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpSessionId.newSessionId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.Test;

public class McpSessionIdTest
{
    private static Supplier<String> randomUuids()
    {
        return () -> UUID.randomUUID().toString();
    }

    @Test
    public void shouldExtractEmbeddedAffinityVerbatim()
    {
        final String sessionId = newSessionId(randomUuids(), 0x1234_5678L);

        assertEquals(0x1234_5678L, extractAffinity(sessionId));
    }

    @Test
    public void shouldExtractZeroAffinity()
    {
        final String sessionId = newSessionId(randomUuids(), 0L);

        assertEquals(0L, extractAffinity(sessionId));
    }

    @Test
    public void shouldMaskAffinityToThirtyTwoBits()
    {
        final String sessionId = newSessionId(randomUuids(), 0xffff_ffff_0000_0001L);

        assertEquals(0x0000_0000_0000_0001L, extractAffinity(sessionId));
    }

    @Test
    public void shouldLeaveRestOfSessionIdUnaffected()
    {
        final Supplier<String> supply = randomUuids();
        final String candidate = supply.get();
        final String sessionId = newSessionId(() -> candidate, 0x42L);

        assertEquals(candidate.substring(0, 28), sessionId.substring(0, 28));
        assertEquals(candidate.length(), sessionId.length());
    }

    @Test
    public void shouldMintDistinctSessionIdsForTheSameAffinity()
    {
        final String first = newSessionId(randomUuids(), 7L);
        final String second = newSessionId(randomUuids(), 7L);

        assertNotEquals(first, second);
        assertEquals(7L, extractAffinity(first));
        assertEquals(7L, extractAffinity(second));
    }

    @Test
    public void shouldAssertWhenCandidateIsNotUuidLength()
    {
        assertThrows(AssertionError.class, () -> newSessionId(() -> "session-1", 7L));
    }

    @Test
    public void shouldAssertWhenSessionIdIsNotUuidLength()
    {
        assertThrows(AssertionError.class, () -> extractAffinity("session-1"));
    }
}
