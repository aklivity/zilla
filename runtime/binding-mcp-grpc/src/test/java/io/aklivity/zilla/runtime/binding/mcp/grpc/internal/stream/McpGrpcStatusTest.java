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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class McpGrpcStatusTest
{
    @Test
    public void shouldNameStandardStatuses()
    {
        assertEquals("CANCELLED", McpGrpcStatus.name("1"));
        assertEquals("NOT_FOUND", McpGrpcStatus.name("5"));
        assertEquals("PERMISSION_DENIED", McpGrpcStatus.name("7"));
        assertEquals("ABORTED", McpGrpcStatus.name("10"));
        assertEquals("UNAVAILABLE", McpGrpcStatus.name("14"));
        assertEquals("UNAUTHENTICATED", McpGrpcStatus.name("16"));
    }

    @Test
    public void shouldFallBackToUnknownForUnrecognizedStatus()
    {
        assertEquals("UNKNOWN", McpGrpcStatus.name("99"));
        assertEquals("UNKNOWN", McpGrpcStatus.name("x"));
        assertEquals("UNKNOWN", McpGrpcStatus.name(null));
    }

    @Test
    public void shouldTreatAbortedAsTransportFailure()
    {
        assertTrue(McpGrpcStatus.transport("10"));
        assertTrue(McpGrpcStatus.transport(null));
        assertFalse(McpGrpcStatus.transport("5"));
    }

    @Test
    public void shouldDescribeStatusWithMessage()
    {
        assertEquals("NOT_FOUND: unknown table", McpGrpcStatus.describe("5", "unknown table"));
        assertEquals("INTERNAL", McpGrpcStatus.describe("13", null));
    }
}
