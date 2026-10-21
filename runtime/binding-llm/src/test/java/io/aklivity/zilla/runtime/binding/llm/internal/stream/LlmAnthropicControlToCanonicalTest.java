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

    @Test
    public void shouldPromoteToolChoiceAuto()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto'}}"),
            json("{'x:tool_choice':'auto'}"));
    }

    @Test
    public void shouldPromoteToolChoiceAny()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'any'}}"),
            json("{'x:tool_choice':'required'}"));
    }

    @Test
    public void shouldPromoteToolChoiceNone()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'none'}}"),
            json("{'x:tool_choice':'none'}"));
    }

    @Test
    public void shouldPromoteToolAsFunction()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'tool','name':'get_weather'}}"),
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"));
    }

    @Test
    public void shouldSplitDisabledParallelUseAsNegatedParallelToolCalls()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto','disable_parallel_tool_use':true}}"),
            json("{'x:tool_choice':'auto','x:parallel_tool_calls':false}"));
    }

    @Test
    public void shouldSplitEnabledParallelUseAsParallelToolCalls()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'disable_parallel_tool_use':false,'type':'any'}}"),
            json("{'x:tool_choice':'required','x:parallel_tool_calls':true}"));
    }

    @Test
    public void shouldPromoteParallelUseWithoutType()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'disable_parallel_tool_use':true}}"),
            json("{'x:parallel_tool_calls':false}"));
    }

    @Test
    public void shouldKeepOtherMembersAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto','extra':[1,{'b':'" + LONG + "'}]}}"),
            json("{'x:tool_choice':'auto','anthropic:tool_choice':{'extra':[1,{'b':'" + LONG + "'}]}}"));
    }

    @Test
    public void shouldKeepUnknownTypeAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'future','name':'f','disable_parallel_tool_use':true}}"),
            json("{'x:parallel_tool_calls':false,'anthropic:tool_choice':{'type':'future','name':'f'}}"));
    }

    @Test
    public void shouldKeepToolWithoutNameAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'tool'}}"),
            json("{'anthropic:tool_choice':{'type':'tool'}}"));
    }

    @Test
    public void shouldKeepNonBooleanParallelUseAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto','disable_parallel_tool_use':'yes'}}"),
            json("{'x:tool_choice':'auto','anthropic:tool_choice':{'disable_parallel_tool_use':'yes'}}"));
    }

    @Test
    public void shouldKeepEmptyObjectAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{}}"),
            json("{'anthropic:tool_choice':{}}"));
    }

    @Test
    public void shouldKeepNonObjectToolChoiceAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':'auto'}"),
            json("{'anthropic:tool_choice':'auto'}"));
    }

    @Test
    public void shouldKeepNullToolChoiceAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':null}"),
            json("{'anthropic:tool_choice':null}"));
    }

    @Test
    public void shouldRejectToolChoiceLargerThanTheCaptureLimit()
    {
        assertRejects(transform, json("{'tool_choice':{'type':'future','name':'" + HUGE + "'}}"));
    }
}
