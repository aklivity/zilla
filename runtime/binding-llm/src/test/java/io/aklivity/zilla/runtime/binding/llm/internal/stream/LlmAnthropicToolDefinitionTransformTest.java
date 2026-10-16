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

public class LlmAnthropicToolDefinitionTransformTest
{
    private static final String LONG = "x".repeat(300);

    private final LlmAnthropicToolDefinitionTransform transform = new LlmAnthropicToolDefinitionTransform();

    @Test
    public void shouldTransformToolToFunction()
    {
        assertTransforms(transform,
            json("{'name':'get_weather','description':'Get the weather'," +
                "'input_schema':{'type':'object','properties':{'city':{'type':'string'}}}}"),
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object','properties':{'city':{'type':'string'}}}}}"));
    }

    @Test
    public void shouldTransformAbsentSchemaAndDescription()
    {
        assertTransforms(transform,
            json("{'name':'ping'}"),
            json("{'type':'function','function':{'name':'ping'}}"));
    }

    @Test
    public void shouldDropDescriptionThatIsNotAString()
    {
        assertTransforms(transform,
            json("{'name':'ping','description':null,'input_schema':{'type':'object'}}"),
            json("{'type':'function','function':{'name':'ping','parameters':{'type':'object'}}}"));
        assertTransforms(transform,
            json("{'name':'ping','description':[1,{'a':2}],'input_schema':{'type':'object'}}"),
            json("{'type':'function','function':{'name':'ping','parameters':{'type':'object'}}}"));
    }

    @Test
    public void shouldTransformNameThatFollowsSchema()
    {
        assertTransforms(transform,
            json("{'description':'Ping','input_schema':{'type':'object'},'name':'ping'}"),
            json("{'type':'function','function':{'description':'Ping','parameters':{'type':'object'},'name':'ping'}}"));
    }

    @Test
    public void shouldTransformLongValues()
    {
        assertTransforms(transform,
            json("{'name':'ping','description':'" + LONG + "','input_schema':" +
                "{'type':'object','properties':{'a':{'type':'string','description':'" + LONG + "'}}}}"),
            json("{'type':'function','function':{'name':'ping','description':'" + LONG + "','parameters':" +
                "{'type':'object','properties':{'a':{'type':'string','description':'" + LONG + "'}}}}}"));
    }

    @Test
    public void shouldDropUnknownMembers()
    {
        assertTransforms(transform,
            json("{'cache_control':{'type':'ephemeral','ttl':[1,{'a':null}]},'name':'ping','note':'" + LONG +
                "','count':12345,'tags':[1,2]}"),
            json("{'type':'function','function':{'name':'ping'}}"));
    }

    @Test
    public void shouldRejectServerToolDefinition()
    {
        assertRejects(transform, json("{'type':'web_search_20250305','name':'web_search'}"));
        assertRejects(transform, json("{'name':'web_search','type':'web_search_20250305'}"));
    }

    @Test
    public void shouldTransformCustomToolType()
    {
        assertTransforms(transform,
            json("{'type':'custom','name':'get_weather','description':'Get the weather'," +
                "'input_schema':{'type':'object','properties':{'city':{'type':'string'}}}}"),
            json("{'type':'function','function':{'name':'get_weather','description':'Get the weather'," +
                "'parameters':{'type':'object','properties':{'city':{'type':'string'}}}}}"));
        assertTransforms(transform,
            json("{'name':'ping','input_schema':{'type':'object'},'type':'custom'}"),
            json("{'type':'function','function':{'name':'ping','parameters':{'type':'object'}}}"));
    }

    @Test
    public void shouldRejectToolTypeThatIsNotCustom()
    {
        assertRejects(transform, json("{'type':'bash_20250124','name':'bash'}"));
        assertRejects(transform, json("{'name':'bash','type':'bash_20250124'}"));
        assertRejects(transform, json("{'type':null,'name':'ping'}"));
        assertRejects(transform, json("{'type':{'a':1},'name':'ping'}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, json("{'description':'Ping'}"));
    }

    @Test
    public void shouldRejectNameThatIsNotAString()
    {
        assertRejects(transform, json("{'name':1}"));
    }

    @Test
    public void shouldRejectSchemaThatIsNotAnObject()
    {
        assertRejects(transform, json("{'name':'ping','input_schema':'x'}"));
        assertRejects(transform, json("{'name':'ping','input_schema':null}"));
    }

    @Test
    public void shouldRejectToolThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }
}
