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

import static io.aklivity.zilla.runtime.binding.mcp.internal.stream.McpServerFactory.redirectHash;
import static java.lang.Integer.toUnsignedLong;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class McpServerFactoryTest
{
    @Test
    public void shouldWidenSessionHashUnsigned() throws Exception
    {
        boolean coveredNegativeHash = false;
        for (int i = 0; i < 5000; i++)
        {
            final String sessionId = "session-" + i;
            coveredNegativeHash |= sessionId.hashCode() < 0;

            assertEquals(sessionId, toUnsignedLong(sessionId.hashCode()), redirectHash(sessionId));
        }
        assertTrue("expected a session id with a negative hashCode in the sample", coveredNegativeHash);
    }

    @Test
    public void shouldNotSignExtendNegativeHash() throws Exception
    {
        final String sessionId = "76da87be-f7f8-4f0f-a2a5-edc2816abaa3";
        assertTrue(sessionId.hashCode() < 0);

        assertTrue(redirectHash(sessionId) != (long) sessionId.hashCode());
        assertEquals(toUnsignedLong(sessionId.hashCode()), redirectHash(sessionId));
    }
}
