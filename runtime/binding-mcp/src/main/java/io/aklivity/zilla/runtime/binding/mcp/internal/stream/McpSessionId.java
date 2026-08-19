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

final class McpSessionId
{
    // A UUID string is 36 characters (8-4-4-4-12 hex groups with hyphens). The RFC 4122
    // "node" field occupies its trailing 12 hex chars; affinity is embedded verbatim in
    // the low 32 bits of that field (the last 8 hex chars, at offset 28). The configured
    // session id supplier is responsible for embedding the given affinity there itself
    // when minting a session id.
    private static final int UUID_LENGTH = 36;
    private static final int AFFINITY_OFFSET = 28;
    private static final int AFFINITY_LENGTH = 8;

    private McpSessionId()
    {
    }

    static long extractAffinity(
        String sessionId)
    {
        assert sessionId.length() == UUID_LENGTH : "session id must be a UUID-length value";
        return Long.parseUnsignedLong(sessionId.substring(AFFINITY_OFFSET, AFFINITY_OFFSET + AFFINITY_LENGTH), 16);
    }
}
