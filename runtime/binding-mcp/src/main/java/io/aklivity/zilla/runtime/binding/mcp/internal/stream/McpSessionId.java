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

import io.aklivity.zilla.runtime.engine.util.function.LongIntPredicate;

final class McpSessionId
{
    private McpSessionId()
    {
    }

    // reject-sample a session id that hash-routes back to this worker; null if exhausted
    static String newSessionId(
        long routedId,
        int attempts,
        Supplier<String> supplySessionId,
        LongIntPredicate isLocalIndex)
    {
        String sessionId = null;

        for (int attempt = 0; attempt < attempts; attempt++)
        {
            final String candidate = supplySessionId.get();
            if (isLocalIndex.test(routedId, candidate.hashCode()))
            {
                sessionId = candidate;
                break;
            }
        }

        return sessionId;
    }
}
