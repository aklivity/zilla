/*
 * Copyright 2021-2024 Aklivity Inc
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmOpenaiToolDefinitionFromCanonicalTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmOpenaiToolDefinitionFromCanonical transform = new LlmOpenaiToolDefinitionFromCanonical();

    @Test
    public void shouldPassCanonicalToolDefinition()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object','properties':{'city':{'type':'string'}}}}}"),
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object','properties':{'city':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldPassMembersInAnyOrder()
    {
        assertTransforms(transform,
            json("{'function':{'parameters':{'type':'object'},'description':'Ping','name':'ping'},'type':'function'}"),
            json("{'function':{'parameters':{'type':'object'},'description':'Ping','name':'ping'},'type':'function'}"));
    }

    @Test
    public void shouldPassUnprefixedMembers()
    {
        assertTransforms(transform,
            json("{'extra':{'a':[1,{'b':'" + LONG + "'}]},'type':'function','function':{'strict':true,'note':'" + LONG +
                "','name':'ping','count':12345,'tags':[1,2]},'tail':null}"),
            json("{'extra':{'a':[1,{'b':'" + LONG + "'}]},'type':'function','function':{'strict':true,'note':'" + LONG +
                "','name':'ping','count':12345,'tags':[1,2]},'tail':null}"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMembers()
    {
        assertTransforms(transform,
            json("{'anthropic:top':{'a':[1,{'b':'" + LONG + "'}]},'type':'function','function':{'anthropic:cache_control':" +
                "{'type':'ephemeral'},'name':'ping','anthropic:note':'" + LONG + "','parameters':{'type':'object'}}}"),
            json("{'type':'function','function':{'name':'ping','parameters':{'type':'object'}}}"));
    }

    @Test
    public void shouldPassOwnPrefixedMembers()
    {
        assertTransforms(transform,
            json("{'type':'function','function':{'openai:note':'x','name':'ping'}}"),
            json("{'type':'function','function':{'openai:note':'x','name':'ping'}}"));
    }

    @Test
    public void shouldRejectToolThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }
}
