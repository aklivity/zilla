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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class LlmBreakpointStashTest
{
    private final LlmBreakpointStash stash = new LlmBreakpointStash();

    @Test
    public void shouldBeAbsentUntilSet()
    {
        assertThat(stash.present(), is(false));
        assertThat(stash.length(), is(0));
    }

    @Test
    public void shouldHoldBreakpointWithoutTtl()
    {
        stash.set(null);

        assertThat(stash.present(), is(true));
        assertThat(text(), equalTo("\"prompt_cache_breakpoint\":{\"mode\":\"explicit\"}"));
    }

    @Test
    public void shouldHoldBreakpointWithTtl()
    {
        stash.set("1h");

        assertThat(text(), equalTo("\"prompt_cache_breakpoint\":{\"mode\":\"explicit\",\"ttl\":\"1h\"}"));
    }

    @Test
    public void shouldOmitTtlThatIsNotPlain()
    {
        stash.set("1\"h");
        assertThat(text(), equalTo("\"prompt_cache_breakpoint\":{\"mode\":\"explicit\"}"));

        stash.set("");
        assertThat(text(), equalTo("\"prompt_cache_breakpoint\":{\"mode\":\"explicit\"}"));

        stash.set("1h".repeat(20));
        assertThat(text(), equalTo("\"prompt_cache_breakpoint\":{\"mode\":\"explicit\"}"));
    }

    @Test
    public void shouldClear()
    {
        stash.set("5m");
        stash.clear();

        assertThat(stash.present(), is(false));
    }

    @Test
    public void shouldCopyFromAnotherStash()
    {
        final LlmBreakpointStash source = new LlmBreakpointStash();

        source.set("5m");
        stash.copyFrom(source);

        assertThat(text(), equalTo("\"prompt_cache_breakpoint\":{\"mode\":\"explicit\",\"ttl\":\"5m\"}"));
    }

    private String text()
    {
        final byte[] bytes = new byte[stash.length()];

        stash.buffer().getBytes(0, bytes);

        return new String(bytes, UTF_8);
    }
}
