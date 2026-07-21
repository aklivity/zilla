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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import java.nio.charset.StandardCharsets;

/**
 * Splices the synthetic search-tool descriptor into a served {@code tools/list} JSON response.
 * <p>
 * Every {@code tools/list} response produced by this codebase (warm-cache or degraded-empty
 * fallback) has the exact, fixed shape {@code {"tools":[...]}} — the prelude and close are
 * literal constants in {@code McpProxyListFactory}/{@code McpProxyToolsListFactory}, never
 * upstream-controlled content. That makes this a safe, cheap byte-array splice rather than
 * requiring a full streaming JSON transform: the array always closes with the literal two bytes
 * {@code ]}}, so inserting before that suffix (with a leading comma when the array is
 * non-empty) is correct by construction, not by guessing the shape.
 * </p>
 * <p>
 * Applied unconditionally, after any scope filtering — the search tool is never subject to
 * per-tool authorization, so it must never be dropped by that filter.
 * </p>
 */
public final class McpSearchToolInjector
{
    private static final byte[] ARRAY_CLOSE = "]}".getBytes(StandardCharsets.UTF_8);
    private static final byte COMMA = ',';
    private static final byte ARRAY_OPEN = '[';

    private McpSearchToolInjector()
    {
    }

    public static byte[] inject(
        byte[] json,
        byte[] toolBytes)
    {
        byte[] result = json;

        if (toolBytes != null && json.length >= ARRAY_CLOSE.length)
        {
            final int splitAt = json.length - ARRAY_CLOSE.length;
            final boolean empty = splitAt > 0 && json[splitAt - 1] == ARRAY_OPEN;
            final int insertLength = toolBytes.length + (empty ? 0 : 1);

            final byte[] injected = new byte[json.length + insertLength];
            System.arraycopy(json, 0, injected, 0, splitAt);

            int offset = splitAt;
            if (!empty)
            {
                injected[offset++] = COMMA;
            }
            System.arraycopy(toolBytes, 0, injected, offset, toolBytes.length);
            offset += toolBytes.length;
            System.arraycopy(ARRAY_CLOSE, 0, injected, offset, ARRAY_CLOSE.length);

            result = injected;
        }

        return result;
    }
}
