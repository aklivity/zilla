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

public class LlmOpenaiResponsesToolDefinitionToCanonicalTest
{
    private final LlmOpenaiResponsesToolDefinitionToCanonical transform = new LlmOpenaiResponsesToolDefinitionToCanonical();

    @Test
    public void shouldNestFunctionTool()
    {
        assertTransforms(transform,
            json("{'type':'function','name':'get_weather','description':'Get','parameters':{'type':'object'},'strict':true}"),
            json("{'type':'function','function':{'name':'get_weather','description':'Get','parameters':{'type':'object'}," +
                "'strict':true}}"));
    }

    @Test
    public void shouldNestFunctionToolWithTypeLast()
    {
        assertTransforms(transform,
            json("{'name':'get_weather','type':'function'}"),
            json("{'type':'function','function':{'name':'get_weather'}}"));
    }

    @Test
    public void shouldPrefixUnknownMembers()
    {
        assertTransforms(transform,
            json("{'type':'function','name':'get_weather','defer_loading':true}"),
            json("{'type':'function','function':{'name':'get_weather','openai:defer_loading':true}}"));
    }

    @Test
    public void shouldDropNonStringDescription()
    {
        assertTransforms(transform,
            json("{'type':'function','name':'get_weather','description':null}"),
            json("{'type':'function','function':{'name':'get_weather'}}"));
    }

    @Test
    public void shouldRejectOtherType()
    {
        assertRejects(transform, json("{'type':'web_search','name':'get_weather'}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, json("{'type':'function'}"));
    }
}
