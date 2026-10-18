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

public class LlmAnthropicToolDefinitionFromCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmAnthropicToolDefinitionFromCanonical transform = new LlmAnthropicToolDefinitionFromCanonical();

    @Test
    public void shouldTransformFunctionToTool()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object','properties':{'city':{'type':'string'}}}}}"),
            json("{'name':'get_weather','description':'Get the weather'," +
                "'input_schema':{'type':'object','properties':{'city':{'type':'string'}}}}"));
    }

    @Test
    public void shouldTransformAbsentParametersToEmptyObjectSchema()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'ping','description':'Ping'}}"),
            json("{'name':'ping','description':'Ping','input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldTransformAbsentDescription()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'ping','parameters':{'type':'object'}}}"),
            json("{'name':'ping','input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldDropDescriptionThatIsNotAString()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'ping','description':null,'parameters':{'type':'object'}}}"),
            json("{'name':'ping','input_schema':{'type':'object'}}"));
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'ping','description':{'a':[1,2]},'parameters':{'type':'object'}}}"),
            json("{'name':'ping','input_schema':{'type':'object'}}"));
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'ping','description':12345,'parameters':{'type':'object'}}}"),
            json("{'name':'ping','input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldTransformNameThatFollowsParameters()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'description':'Ping','parameters':{'type':'object'},'name':'ping'}}"),
            json("{'description':'Ping','input_schema':{'type':'object'},'name':'ping'}"));
    }

    @Test
    public void shouldTransformLongValues()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'ping','description':'" + LONG + "','parameters':" +
                "{'type':'object','properties':{'a':{'type':'string','description':'" + LONG + "'}}}}}"),
            json("{'name':'ping','description':'" + LONG + "','input_schema':" +
                "{'type':'object','properties':{'a':{'type':'string','description':'" + LONG + "'}}}}"));
    }

    @Test
    public void shouldStripOwnPrefixAtTheirPosition()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'anthropic:cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]}," +
                "'name':'ping','anthropic:note':'" + LONG + "','anthropic:count':12345,'anthropic:tags':[1,2]}}"),
            json("{'cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]},'name':'ping','note':'" + LONG +
                "','count':12345,'tags':[1,2],'input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMembers()
    {
        assertTransforms(transform,
            json("{'extra':1,'gemini:top':{'a':[1,{'b':'" + LONG + "'}]},'type':'function','function':{'openai:strict':true," +
                "'name':'ping','gemini:note':'" + LONG + "','parameters':{'type':'object'}},'tail':null}"),
            json("{'extra':1,'name':'ping','input_schema':{'type':'object'},'tail':null}"));
    }

    @Test
    public void shouldPassUnprefixedUnknownMembers()
    {
        assertTransforms(transform,
            json("{'extra':{'a':[1,{'b':'" + LONG + "'}]},'type':'function','function':{'strict':true,'note':'" + LONG +
                "','name':'ping','count':12345,'tags':[1,2]},'tail':null}"),
            json("{'extra':{'a':[1,{'b':'" + LONG + "'}]},'strict':true,'note':'" + LONG +
                "','name':'ping','count':12345,'tags':[1,2],'input_schema':{'type':'object'},'tail':null}"));
    }

    @Test
    public void shouldTransformValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'description':'" + HUGE + "','name':'ping','parameters':{'type':'object'}}}"),
            json("{'description':'" + HUGE + "','name':'ping','input_schema':{'type':'object'}}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, json("{'type':'function','function':{'description':'Ping'}}"));
    }

    @Test
    public void shouldRejectNameThatIsNotAString()
    {
        assertRejects(transform, json("{'type':'function','function':{'name':1}}"));
    }

    @Test
    public void shouldRejectMissingFunction()
    {
        assertRejects(transform, json("{'type':'custom','custom':{'name':'ping'}}"));
    }

    @Test
    public void shouldRejectFunctionThatIsNotAnObject()
    {
        assertRejects(transform, json("{'type':'function','function':'ping'}"));
    }

    @Test
    public void shouldRejectParametersThatAreNotAnObject()
    {
        assertRejects(transform, json("{'type':'function','function':{'name':'ping','parameters':'x'}}"));
        assertRejects(transform, json("{'type':'function','function':{'name':'ping','parameters':null}}"));
    }

    @Test
    public void shouldRejectToolThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }
}
