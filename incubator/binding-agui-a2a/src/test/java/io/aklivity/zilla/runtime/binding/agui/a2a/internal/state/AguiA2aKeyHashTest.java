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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class AguiA2aKeyHashTest
{
    @Test
    public void shouldHashFixedKeys()
    {
        assertThat(AguiA2aKeyHash.hash("alice", "weather", "thread-1"), equalTo(0xedf769c7e5d5da5bL));
        assertThat(AguiA2aKeyHash.hash(null, "weather", "thread-1"), equalTo(0x68a9009bad8215bcL));
        assertThat(AguiA2aKeyHash.hash("", "weather", "thread-1"), equalTo(0x5faa62a922d35c1dL));
        assertThat(AguiA2aKeyHash.hash("alice", "weather", "thread-2"), equalTo(0x2b1d87bd42c75981L));
    }

    @Test
    public void shouldHashMultiByteUtf8()
    {
        assertThat(AguiA2aKeyHash.hash("alice", "weather", "é€😀"), equalTo(0x4a1769c813bbe076L));
    }

    @Test
    public void shouldHashThreadIdFromBuffer()
    {
        final byte[] bytes = "xxthread-1yy".getBytes(StandardCharsets.UTF_8);

        assertThat(AguiA2aKeyHash.hash("alice", "weather", new UnsafeBuffer(bytes), 2, 8),
            equalTo(AguiA2aKeyHash.hash("alice", "weather", "thread-1")));
    }

    @Test
    public void shouldDistinguishAbsentFromEmptyIdentity()
    {
        assertThat(AguiA2aKeyHash.hash(null, "weather", "thread-1"),
            not(equalTo(AguiA2aKeyHash.hash("", "weather", "thread-1"))));
    }

    @Test
    public void shouldNotConfuseFieldBoundaries()
    {
        assertThat(AguiA2aKeyHash.hash("ab", "c", "thread-1"),
            not(equalTo(AguiA2aKeyHash.hash("a", "bc", "thread-1"))));
    }

    @Test
    public void shouldMatchThreadKeyHash()
    {
        assertThat(new AguiA2aThreadKey("alice", "weather", "thread-1").hash(), equalTo(0xedf769c7e5d5da5bL));
    }
}
