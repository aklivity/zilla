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

public class LlmOpenaiToolCallTransformTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmOpenaiToolCallTransform transform = new LlmOpenaiToolCallTransform();

    @Test
    public void shouldTransformToolCallToToolUse()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':" +
                quote(json("{'city':'Paris'}")) + "}}"),
            json("{'type':'tool_use','id':'call_1','name':'get_weather','input':{'city':'Paris'}}"));
    }

    @Test
    public void shouldTransformEmptyArgumentsToEmptyInput()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':''}}"),
            json("{'type':'tool_use','id':'call_1','name':'ping','input':{}}"));
    }

    @Test
    public void shouldTransformAbsentArgumentsToEmptyInput()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping'}}"),
            json("{'type':'tool_use','id':'call_1','name':'ping','input':{}}"));
    }

    @Test
    public void shouldTransformMembersInAnyOrder()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'arguments':" + quote(json("{'a':1}")) + ",'name':'ping'},'id':'call_1'}"),
            json("{'type':'tool_use','input':{'a':1},'name':'ping','id':'call_1'}"));
    }

    @Test
    public void shouldTransformEscapedAndNestedArguments()
    {
        final String arguments = "{\"q\":\"say \\\"hi\\\" \\\\ \\u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}";

        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':") + quote(arguments) + "}}",
            json("{'type':'tool_use','id':'call_1','name':'ping','input':") +
                "{\"q\":\"say \\\"hi\\\" \\\\ \u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}}");
    }

    @Test
    public void shouldTransformLongValues()
    {
        assertTransforms(transform,
            json("{'id':'" + LONG + "','type':'function','function':{'name':'" + LONG + "','arguments':") +
                quote(json("{'text':'" + LONG + "'}")) + "}}",
            json("{'type':'tool_use','id':'" + LONG + "','name':'" + LONG + "','input':{'text':'" + LONG + "'}}"));
    }

    @Test
    public void shouldDropUnknownMembers()
    {
        assertTransforms(transform,
            json("{'index':0,'id':'call_1','extra':{'x':[1,{'y':2}],'z':'" + LONG + "'},'type':'function','function':" +
                "{'strict':true,'name':'ping','note':'" + LONG + "','arguments':" + quote("{}") + ",'count':12345}," +
                "'tail':[" + LONG.length() + "]}"),
            json("{'type':'tool_use','id':'call_1','name':'ping','input':{}}"));
    }

    @Test
    public void shouldRejectMalformedArguments()
    {
        assertRejects(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':'{oops'}}"));
    }

    @Test
    public void shouldRejectArgumentsThatAreNotAnObject()
    {
        assertRejects(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':'[1]'}}"));
        assertRejects(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':'5'}}"));
        assertRejects(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':null}}"));
    }

    @Test
    public void shouldRejectMissingId()
    {
        assertRejects(transform,
            json("{'type':'function','function':{'name':'ping','arguments':'{}'}}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform,
            json("{'id':'call_1','type':'function','function':{'arguments':'{}'}}"));
    }

    @Test
    public void shouldRejectMissingFunction()
    {
        assertRejects(transform, json("{'id':'call_1','type':'function'}"));
    }

    @Test
    public void shouldRejectFunctionThatIsNotAnObject()
    {
        assertRejects(transform, json("{'id':'call_1','type':'function','function':'ping'}"));
    }

    @Test
    public void shouldRejectIdThatIsNotAString()
    {
        assertRejects(transform,
            json("{'id':1,'type':'function','function':{'name':'ping','arguments':'{}'}}"));
    }

    @Test
    public void shouldRejectToolCallThatIsNotAnObject()
    {
        assertRejects(transform, json("['call_1']"));
        assertRejects(transform, json("'call_1'"));
    }
}
