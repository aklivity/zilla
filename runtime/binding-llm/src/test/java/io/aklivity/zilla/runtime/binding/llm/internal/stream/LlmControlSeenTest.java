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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class LlmControlSeenTest
{
    @Test
    public void shouldCarryRepeatedMember()
    {
        final LlmControlSeen seen = new LlmControlSeen();

        assertThat(seen.carried("created", -1), is(false));
        assertThat(seen.carried("created", -1), is(true));
    }

    @Test
    public void shouldDistinguishMessages()
    {
        final LlmControlSeen seen = new LlmControlSeen();

        assertThat(seen.carried("logprobs", 0), is(false));
        assertThat(seen.carried("logprobs", 1), is(false));
        assertThat(seen.carried("logprobs", 0), is(true));
        assertThat(seen.carried("logprobs", 1), is(true));
    }

    @Test
    public void shouldCompareCharSequenceContent()
    {
        final LlmControlSeen seen = new LlmControlSeen();

        assertThat(seen.carried(new StringBuilder("service_tier"), -1), is(false));
        assertThat(seen.carried("service_tier", -1), is(true));
    }

    @Test
    public void shouldRememberEveryDistinctMember()
    {
        final LlmControlSeen seen = new LlmControlSeen();
        final int members = 100;

        for (int index = 0; index < members; index++)
        {
            assertThat("first " + index, seen.carried("member" + index, -1), is(false));
        }

        for (int index = 0; index < members; index++)
        {
            assertThat("repeat " + index, seen.carried("member" + index, -1), is(true));
        }
    }
}
