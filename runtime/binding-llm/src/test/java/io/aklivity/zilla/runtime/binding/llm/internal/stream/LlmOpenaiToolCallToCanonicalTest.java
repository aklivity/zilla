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

public class LlmOpenaiToolCallToCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmOpenaiToolCallToCanonical transform = new LlmOpenaiToolCallToCanonical();

    @Test
    public void shouldUnescapeArguments()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'get_weather','arguments':" +
                quote(json("{'city':'Paris'}")) + "}}"),
            json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldWrapEmptyArgumentsAsEmptyObject()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':''}}"),
            json("{'arguments':{}}"));
    }

    @Test
    public void shouldWrapAbsentArgumentsAsEmptyObject()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping'}}"),
            json("{'arguments':{}}"));
    }

    @Test
    public void shouldUnescapeArgumentsInAnyOrder()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'arguments':" + quote(json("{'a':1}")) + ",'name':'ping'},'id':'call_1'}"),
            json("{'arguments':{'a':1}}"));
    }

    @Test
    public void shouldUnescapeEscapedAndNestedArguments()
    {
        final String arguments = "{\"q\":\"say \\\"hi\\\" \\\\ \\u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}";

        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':") + quote(arguments) + "}}",
            "{\"arguments\":{\"q\":\"say \\\"hi\\\" \\\\ \u00e9 \\n\",\"n\":[1,2.5,true,null,{\"k\":\"v\"}],\"e\":{}}}");
    }

    @Test
    public void shouldUnescapeLongValues()
    {
        assertTransforms(transform,
            json("{'id':'" + LONG + "','type':'function','function':{'name':'" + LONG + "','arguments':") +
                quote(json("{'text':'" + LONG + "'}")) + "}}",
            json("{'arguments':{'text':'" + LONG + "'}}"));
    }

    @Test
    public void shouldUnescapeValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':") +
                quote(json("{'text':'" + HUGE + "'}")) + "}}",
            json("{'arguments':{'text':'" + HUGE + "'}}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembersBeforeArguments()
    {
        assertTransforms(transform,
            json("{'index':0,'id':'call_1','type':'function','function':{'name':'ping','arguments':" +
                quote(json("{'a':1}")) + "}}"),
            json("{'openai:index':0,'arguments':{'a':1}}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembersAfterArguments()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'name':'ping','arguments':" +
                quote(json("{'a':1}")) + "},'extra':{'x':[1,{'y':2}],'z':'" + LONG + "'},'tail':null}"),
            json("{'arguments':{'a':1},'openai:extra':{'x':[1,{'y':2}],'z':'" + LONG + "'},'openai:tail':null}"));
    }

    @Test
    public void shouldCarryNativeOnlyMembersWithoutArguments()
    {
        assertTransforms(transform,
            json("{'index':0,'id':'call_1','type':'function','function':{'name':'ping'},'tail':null}"),
            json("{'openai:index':0,'arguments':{},'openai:tail':null}"));
    }

    @Test
    public void shouldDropUnknownFunctionMembers()
    {
        assertTransforms(transform,
            json("{'id':'call_1','type':'function','function':{'strict':true,'note':'" + LONG + "','name':'ping'," +
                "'arguments':" + quote(json("{'a':1}")) + "}}"),
            json("{'arguments':{'a':1}}"));
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
    public void shouldRejectToolCallThatIsNotAnObject()
    {
        assertRejects(transform, json("['call_1']"));
        assertRejects(transform, json("'call_1'"));
    }
}
