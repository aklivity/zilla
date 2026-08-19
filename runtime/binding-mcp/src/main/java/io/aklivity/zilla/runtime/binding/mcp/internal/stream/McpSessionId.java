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

import java.util.function.Supplier;

final class McpSessionId
{
    // RFC 4122 "node" field occupies the trailing 12 hex chars of a UUID string;
    // affinity is embedded verbatim in its low 32 bits (8 hex chars), leaving the rest
    // of the id, node field included, exactly as generated (CSPRNG-random). The
    // configured session id supplier is a pluggable SPI hook and may return a
    // shorter, deterministic value (e.g. a fixed test fixture); such candidates are
    // too short to embed into and are returned verbatim, with affinity extraction
    // falling back to the candidate's own hash so pinning stays deterministic.
    private static final int AFFINITY_OFFSET = 28;
    private static final int AFFINITY_LENGTH = 8;
    private static final int MIN_EMBEDDABLE_LENGTH = AFFINITY_OFFSET + AFFINITY_LENGTH;

    private McpSessionId()
    {
    }

    static String newSessionId(
        Supplier<String> supplySessionId,
        long affinity)
    {
        final String candidate = supplySessionId.get();
        final String sessionId;
        if (candidate.length() >= MIN_EMBEDDABLE_LENGTH)
        {
            final String affinityHex = String.format("%08x", affinity & 0xffff_ffffL);
            sessionId = candidate.substring(0, AFFINITY_OFFSET) + affinityHex;
        }
        else
        {
            sessionId = candidate;
        }
        return sessionId;
    }

    static long extractAffinity(
        String sessionId)
    {
        return sessionId.length() >= MIN_EMBEDDABLE_LENGTH
            ? Long.parseUnsignedLong(sessionId.substring(AFFINITY_OFFSET, AFFINITY_OFFSET + AFFINITY_LENGTH), 16)
            : Integer.toUnsignedLong(sessionId.hashCode());
    }

    static boolean hasEmbeddedAffinity(
        String sessionId)
    {
        return sessionId.length() >= MIN_EMBEDDABLE_LENGTH;
    }
}
