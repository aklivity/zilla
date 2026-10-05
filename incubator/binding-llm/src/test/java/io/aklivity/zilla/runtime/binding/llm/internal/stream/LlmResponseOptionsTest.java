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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LlmResponseOptionsTest
{
    @Test
    public void shouldReportNothingWhenAbsent()
    {
        final LlmResponseOptions options = new LlmResponseOptions("created", "service_tier");

        assertEquals(LlmResponseOptions.NONE, options.next());
    }

    @Test
    public void shouldDeliverMembersInArrivalOrder()
    {
        final LlmResponseOptions options = new LlmResponseOptions("created", "service_tier", "system_fingerprint");

        options.set(2, "\"fp_abc\"");
        options.set(0, "1700000000");

        assertEquals(2, options.next());
        assertEquals("system_fingerprint", options.member(2));
        assertEquals("\"fp_abc\"", options.latest(2));

        options.deliver(2);

        assertEquals(0, options.next());

        options.deliver(0);

        assertEquals(LlmResponseOptions.NONE, options.next());
    }

    @Test
    public void shouldNotRepeatDeliveredMembers()
    {
        final LlmResponseOptions options = new LlmResponseOptions("created");

        options.set(0, "1700000000");
        options.deliver(0);
        options.set(0, "1700000000");

        assertEquals(LlmResponseOptions.NONE, options.next());
    }

    @Test
    public void shouldDeliverChangedMembersAgain()
    {
        final LlmResponseOptions options = new LlmResponseOptions("service_tier");

        options.set(0, "\"default\"");
        options.deliver(0);
        options.set(0, "\"scale\"");

        assertEquals(0, options.next());
        assertEquals("\"scale\"", options.latest(0));
    }

    @Test
    public void shouldClear()
    {
        final LlmResponseOptions options = new LlmResponseOptions("created");

        options.set(0, "1700000000");
        options.deliver(0);
        options.clear();
        options.set(0, "1700000000");

        assertEquals(0, options.next());
    }
}
