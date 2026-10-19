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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class LlmAnthropicControlToCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmAnthropicControlToCanonical transform = new LlmAnthropicControlToCanonical();

    @Test
    public void shouldPrefixScalarMember()
    {
        assertTransforms(transform,
            json("{'temperature':0.7}"),
            json("{'anthropic:temperature':0.7}"));
    }

    @Test
    public void shouldPrefixStringMember()
    {
        assertTransforms(transform,
            json("{'stop_sequence':'" + LONG + "'}"),
            json("{'anthropic:stop_sequence':'" + LONG + "'}"));
    }

    @Test
    public void shouldPrefixNullMember()
    {
        assertTransforms(transform,
            json("{'stop_sequence':null}"),
            json("{'anthropic:stop_sequence':null}"));
    }

    @Test
    public void shouldMoveObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'metadata':{'user_id':'u','a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}"),
            json("{'anthropic:metadata':{'user_id':'u','a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}"));
    }

    @Test
    public void shouldMoveArrayValueUnchanged()
    {
        assertTransforms(transform,
            json("{'stop_sequences':['a','b',{'c':1}]}"),
            json("{'anthropic:stop_sequences':['a','b',{'c':1}]}"));
    }

    @Test
    public void shouldMoveValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'mcp_servers':[{'note':'" + HUGE + "'}]}"),
            json("{'anthropic:mcp_servers':[{'note':'" + HUGE + "'}]}"));
    }

    @Test
    public void shouldPrefixStreamOptionsAsNativeMember()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':false}}"),
            json("{'anthropic:stream_options':{'include_usage':false}}"));

        assertThat(transform.streamSeen(), is(false));
    }

    @Test
    public void shouldCarryStreamUnprefixed()
    {
        assertTransforms(transform, json("{'stream':true}"), json("{'stream':true}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(true));
    }

    @Test
    public void shouldCarryStreamFalse()
    {
        assertTransforms(transform, json("{'stream':false}"), json("{'stream':false}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldCarryStreamNull()
    {
        assertTransforms(transform, json("{'stream':null}"), json("{'stream':null}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldResetStreamState()
    {
        assertTransforms(transform, json("{'stream':true}"), json("{'stream':true}"));
        assertTransforms(transform, json("{'temperature':1}"), json("{'anthropic:temperature':1}"));

        assertThat(transform.streamSeen(), is(false));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldRejectStreamThatIsNotBoolean()
    {
        assertRejects(transform, json("{'stream':'yes'}"));
        assertRejects(transform, json("{'stream':{}}"));
    }
}
