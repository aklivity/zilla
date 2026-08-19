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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class McpSessionIdTest
{
    @Test
    public void shouldExtractEmbeddedAffinityVerbatim()
    {
        assertEquals(0x1234_5678L, extractAffinity("5ca1ab1e-c0de-4a11-5e55-000012345678"));
    }

    @Test
    public void shouldExtractZeroAffinity()
    {
        assertEquals(0L, extractAffinity("5ca1ab1e-c0de-4a11-5e55-000100000000"));
    }

    @Test
    public void shouldAssertWhenSessionIdIsNotUuidLength()
    {
        assertThrows(AssertionError.class, () -> extractAffinity("session-1"));
    }
}
