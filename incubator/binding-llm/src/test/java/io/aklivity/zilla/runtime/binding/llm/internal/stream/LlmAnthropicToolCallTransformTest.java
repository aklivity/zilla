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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.quote;

import org.junit.Test;

public class LlmAnthropicToolCallTransformTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmAnthropicToolCallTransform transform = new LlmAnthropicToolCallTransform();

    @Test
    public void shouldTransformToolUseToToolCall()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'city':'Paris'}}"),
            json("{'id':'toolu_1','type':'function','function':{'name':'get_weather','arguments':") +
                quote(json("{'city':'Paris'}")) + "}}");
    }

    @Test
    public void shouldTransformAbsentInputToEmptyArguments()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping'}"),
            json("{'id':'toolu_1','type':'function','function':{'name':'ping','arguments':'{}'}}"));
    }

    @Test
    public void shouldTransformIdThatFollowsInput()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','name':'ping','input':{'a':1},'id':'toolu_1'}"),
            json("{'type':'function','function':{'name':'ping','arguments':") + quote(json("{'a':1}")) +
                json("},'id':'toolu_1'}"));
    }

    @Test
    public void shouldTransformIdBetweenNameAndInput()
    {
        assertTransforms(transform,
            json("{'name':'ping','id':'toolu_1','input':{'a':1}}"),
            json("{'type':'function','function':{'name':'ping','arguments':") + quote(json("{'a':1}")) +
                json("},'id':'toolu_1'}"));
    }

    @Test
    public void shouldTransformNameThatFollowsInput()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','input':{'a':1},'name':'ping'}"),
            json("{'id':'toolu_1','type':'function','function':{'arguments':") + quote(json("{'a':1}")) +
                json(",'name':'ping'}}"));
    }

    @Test
    public void shouldTransformInputThatPrecedesIdAndName()
    {
        assertTransforms(transform,
            json("{'input':{'a':1},'name':'ping','id':'toolu_1'}"),
            json("{'type':'function','function':{'arguments':") + quote(json("{'a':1}")) +
                json(",'name':'ping'},'id':'toolu_1'}"));
    }

    @Test
    public void shouldTransformEscapedAndNestedInput()
    {
        final String input = "{\"q\":\"say \\\"hi\\\" \\\\ \\u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}";
        final String canonical = "{\"q\":\"say \\\"hi\\\" \\\\ \u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}";

        assertTransforms(transform,
            json("{'type':'tool_use','id':'toolu_1','name':'ping','input':") + input + "}",
            json("{'id':'toolu_1','type':'function','function':{'name':'ping','arguments':") + quote(canonical) + "}}");
    }

    @Test
    public void shouldTransformLongValues()
    {
        assertTransforms(transform,
            json("{'type':'tool_use','name':'" + LONG + "','id':'" + LONG + "','input':{'text':'" + LONG + "'}}"),
            json("{'type':'function','function':{'name':'" + LONG + "','arguments':") +
                quote(json("{'text':'" + LONG + "'}")) + json("},'id':'" + LONG + "'}"));
    }

    @Test
    public void shouldDropUnknownMembers()
    {
        assertTransforms(transform,
            json("{'cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]},'type':'tool_use','id':'toolu_1'," +
                "'note':'" + LONG + "','name':'ping','count':12345,'input':{}}"),
            json("{'id':'toolu_1','type':'function','function':{'name':'ping','arguments':'{}'}}"));
    }

    @Test
    public void shouldRejectIdThatIsTooLongToHold()
    {
        assertRejects(transform,
            json("{'type':'tool_use','name':'ping','id':'" + "x".repeat(5000) + "','input':{}}"));
    }

    @Test
    public void shouldRejectInputThatIsNotAnObject()
    {
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':'ping','input':'{}'}"));
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':'ping','input':[1]}"));
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':'ping','input':null}"));
    }

    @Test
    public void shouldRejectMissingId()
    {
        assertRejects(transform, json("{'type':'tool_use','name':'ping','input':{}}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','input':{}}"));
    }

    @Test
    public void shouldRejectNameThatIsNotAString()
    {
        assertRejects(transform, json("{'type':'tool_use','id':'toolu_1','name':1,'input':{}}"));
    }

    @Test
    public void shouldRejectIdThatIsNotAString()
    {
        assertRejects(transform, json("{'type':'tool_use','name':'ping','id':[1],'input':{}}"));
    }

    @Test
    public void shouldRejectToolUseThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }
}
