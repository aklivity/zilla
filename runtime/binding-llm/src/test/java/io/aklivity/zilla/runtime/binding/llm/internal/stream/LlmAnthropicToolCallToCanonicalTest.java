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

import org.junit.Test;

public class LlmAnthropicToolCallToCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmAnthropicToolCallToCanonical transform = new LlmAnthropicToolCallToCanonical();

    @Test
    public void shouldWrapInputAsArguments()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'city':'Paris'}}"),
            json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldWrapAbsentInputAsEmptyArguments()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping'}"),
            json("{'arguments':{}}"));
    }

    @Test
    public void shouldWrapInputInAnyOrder()
    {
        assertTransforms(transform,
            json("{'input':{'a':1},'name':'ping','id':'toolu_1','type':'tool_use'}"),
            json("{'arguments':{'a':1}}"));
    }

    @Test
    public void shouldWrapNestedInput()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}}"),
            json("{'arguments':{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}}"));
    }

    @Test
    public void shouldWrapLongValues()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'" + LONG + "','name':'" + LONG + "','input':{'text':'" + LONG + "'}}"),
            json("{'arguments':{'text':'" + LONG + "'}}"));
    }

    @Test
    public void shouldWrapValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':{'text':'" + HUGE + "'}}"),
            json("{'arguments':{'text':'" + HUGE + "'}}"));
    }

    @Test
    public void shouldCarryCacheControlBeforeInput()
    {
        assertTransforms(transform,
            json("{'cache_control':{'type':'ephemeral'},'type':'tool_use','id':'toolu_1','name':'ping','input':{'a':1}}"),
            json("{'anthropic:cache_control':{'type':'ephemeral'},'arguments':{'a':1}}"));
    }

    @Test
    public void shouldCarryCacheControlAfterInput()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':{'a':1},'cache_control':{'type':'ephemeral'}}"),
            json("{'arguments':{'a':1},'anthropic:cache_control':{'type':'ephemeral'}}"));
    }

    @Test
    public void shouldCarryCacheControlWithoutInput()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping','cache_control':{'type':'ephemeral'}}"),
            json("{'anthropic:cache_control':{'type':'ephemeral'},'arguments':{}}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembers()
    {
        assertTransforms(transform,
            json("{'cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]},'type':'tool_use','id':'toolu_1'," +
                "'note':'" + LONG + "','name':'ping','input':{'a':1},'count':12345,'tail':null}"),
            json("{'anthropic:cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]},'anthropic:note':'" + LONG +
                "','arguments':{'a':1},'anthropic:count':12345,'anthropic:tail':null}"));
    }

    @Test
    public void shouldRejectInputThatIsNotAnObject()
    {
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':'ping','input':'x'}"));
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':'ping','input':[1]}"));
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':'ping','input':null}"));
    }

    @Test
    public void shouldRejectToolUseThatIsNotAnObject()
    {
        assertRejects(transform, json("['toolu_1']"));
        assertRejects(transform, json("'toolu_1'"));
    }
}
