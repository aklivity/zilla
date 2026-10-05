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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmOptionsTest
{
    @Test
    public void shouldResolveSlotsByMember()
    {
        final LlmOptions options = new LlmOptions("stream", "temperature", "top_p");

        assertEquals(0, options.slot("stream"));
        assertEquals(2, options.slot("top_p"));
        assertEquals(LlmOptions.NONE, options.slot("model"));
        assertEquals(LlmOptions.NONE, options.slot("temp"));
    }

    @Test
    public void shouldTrackPresence()
    {
        final LlmOptions options = new LlmOptions("stream", "temperature");

        assertTrue(options.isEmpty());

        options.set(1, "0.7");
        options.set(1, "0.8");

        assertFalse(options.isEmpty());
        assertEquals("0.8", options.get(1));
        assertNull(options.get(0));

        options.clear();

        assertTrue(options.isEmpty());
        assertNull(options.get(1));
    }

    @Test
    public void shouldRenderMembersInSlotOrder()
    {
        final LlmOptions options = new LlmOptions("stream", "temperature", "stop");

        options.set(2, "[\"END\"]");
        options.set(1, "0.7");

        assertEquals(",\"temperature\":0.7,\"stop\":[\"END\"]", new String(options.members(true), UTF_8));
        assertEquals("\"temperature\":0.7,\"stop\":[\"END\"]", new String(options.members(false), UTF_8));
    }

    @Test
    public void shouldRenderNothingWhenEmpty()
    {
        assertEquals(0, new LlmOptions("stream").members(true).length);
    }
}
