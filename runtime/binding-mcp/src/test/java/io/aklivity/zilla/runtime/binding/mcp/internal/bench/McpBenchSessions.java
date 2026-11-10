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
package io.aklivity.zilla.runtime.binding.mcp.internal.bench;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mints predictable session identifiers, so a benchmark can build the request headers of a session before the
 * binding has minted it. The identifier of a session that faces an application stream peer embeds the affinity
 * of the external binding, which is where the peer's streams are routed. Identifiers cycle through
 * {@code capacity} values, so a session must be closed before the identifier it holds comes round again. When
 * several bindings in a pipeline each mint an identifier for the same session, they all mint the same one.
 */
public final class McpBenchSessions
{
    static final long EXTERNAL_AFFINITY = 0x3fL;
    private static final AtomicInteger NEXT = new AtomicInteger();

    private static volatile int mask;
    private static volatile int mints;

    private McpBenchSessions()
    {
    }

    public static void reset(
        int capacity,
        int bindings)
    {
        mask = capacity - 1;
        mints = bindings;
        NEXT.set(0);
    }

    public static String sessionId(
        long affinity)
    {
        return identifier(NEXT.getAndIncrement() / mints, 0L);
    }

    public static String externalSessionId(
        long affinity)
    {
        return identifier(NEXT.getAndIncrement() / mints, EXTERNAL_AFFINITY);
    }

    public static String identifier(
        int ordinal,
        long affinity)
    {
        return String.format("5ca1ab1e-c0de-4a11-b0a7-%04x%08x", (ordinal & mask) + 1, affinity);
    }
}
