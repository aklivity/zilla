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

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmLockstepTest
{
    private final LlmLockstep lockstep = new LlmLockstep(1024);

    @Test
    public void shouldMatchOutputThatEchoesInput()
    {
        lockstep.begin();
        lockstep.consumed(bytes("Hello"), 0, 5);

        assertThat(lockstep.produced(bytes("Hello"), 0, 5), equalTo(5));
        assertThat(lockstep.diverged(), equalTo(false));
    }

    @Test
    public void shouldMatchOutputThatLagsInput()
    {
        lockstep.begin();
        lockstep.consumed(bytes("Hello"), 0, 5);

        assertThat(lockstep.produced(bytes("Hel"), 0, 3), equalTo(3));

        lockstep.consumed(bytes(", world"), 0, 7);

        assertThat(lockstep.produced(bytes("lo, world"), 0, 9), equalTo(9));
        assertThat(lockstep.diverged(), equalTo(false));
    }

    @Test
    public void shouldStopMatchingAtFirstDifferingByte()
    {
        lockstep.begin();
        lockstep.consumed(bytes("Hi there secret key"), 0, 19);

        assertThat(lockstep.produced(bytes("Hi there ****** key"), 0, 19), equalTo(9));
        assertThat(lockstep.diverged(), equalTo(true));
    }

    @Test
    public void shouldNotMatchAfterDivergence()
    {
        lockstep.begin();
        lockstep.consumed(bytes("abc"), 0, 3);
        lockstep.produced(bytes("aXc"), 0, 3);
        lockstep.consumed(bytes("def"), 0, 3);

        assertThat(lockstep.produced(bytes("def"), 0, 3), equalTo(0));
        assertThat(lockstep.diverged(), equalTo(true));
    }

    @Test
    public void shouldDivergeWhenOutputExceedsInput()
    {
        lockstep.begin();
        lockstep.consumed(bytes("abc"), 0, 3);

        assertThat(lockstep.produced(bytes("abcd"), 0, 4), equalTo(3));
        assertThat(lockstep.diverged(), equalTo(true));
    }

    @Test
    public void shouldDivergeWhenLagExceedsCapacity()
    {
        final LlmLockstep small = new LlmLockstep(4);
        small.begin();
        small.consumed(bytes("abcdef"), 0, 6);

        assertThat(small.diverged(), equalTo(true));
    }

    @Test
    public void shouldBeginAfreshForNextSection()
    {
        lockstep.begin();
        lockstep.consumed(bytes("abc"), 0, 3);
        lockstep.produced(bytes("xyz"), 0, 3);
        lockstep.begin();
        lockstep.consumed(bytes("abc"), 0, 3);

        assertThat(lockstep.produced(bytes("abc"), 0, 3), equalTo(3));
        assertThat(lockstep.diverged(), equalTo(false));
    }

    private static DirectBufferEx bytes(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
