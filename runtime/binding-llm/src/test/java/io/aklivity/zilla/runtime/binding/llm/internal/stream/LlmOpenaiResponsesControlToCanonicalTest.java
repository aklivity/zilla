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

public class LlmOpenaiResponsesControlToCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmOpenaiResponsesControlToCanonical transform = new LlmOpenaiResponsesControlToCanonical();

    @Test
    public void shouldPromoteToolChoiceAuto()
    {
        assertTransforms(transform,
            json("{'tool_choice':'auto'}"),
            json("{'x:tool_choice':'auto'}"));
    }

    @Test
    public void shouldPromoteToolChoiceRequired()
    {
        assertTransforms(transform,
            json("{'tool_choice':'required'}"),
            json("{'x:tool_choice':'required'}"));
    }

    @Test
    public void shouldPromoteToolChoiceNone()
    {
        assertTransforms(transform,
            json("{'tool_choice':'none'}"),
            json("{'x:tool_choice':'none'}"));
    }

    @Test
    public void shouldNestFunctionToolChoice()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'name':'get_weather','type':'function'}}"),
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"));
    }

    @Test
    public void shouldNestAllowedToolsToolChoice()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'allowed_tools','mode':'required'," +
                "'tools':[{'type':'function','name':'a'},{'type':'function','name':'b'}]}}"),
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'required'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"));
    }

    @Test
    public void shouldNestAllowedToolsWithoutTools()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'allowed_tools','mode':'auto','tools':[]}}"),
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'auto','tools':[]}}}"));
    }

    @Test
    public void shouldKeepHostedToolChoiceAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'file_search'}}"),
            json("{'openai:tool_choice':{'type':'file_search'}}"));
    }

    @Test
    public void shouldKeepAllowedToolsWithHostedToolAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'allowed_tools','mode':'auto','tools':[{'type':'mcp','server_label':'s'}]}}"),
            json("{'openai:tool_choice':{'type':'allowed_tools','mode':'auto','tools':[{'type':'mcp','server_label':'s'}]}}"));
    }

    @Test
    public void shouldKeepFunctionWithExtraMemberAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'function','name':'f','strict':true}}"),
            json("{'openai:tool_choice':{'type':'function','name':'f','strict':true}}"));
    }

    @Test
    public void shouldKeepUnknownStringAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':'bogus'}"),
            json("{'openai:tool_choice':'bogus'}"));
    }

    @Test
    public void shouldRejectToolChoiceLargerThanTheCaptureLimit()
    {
        assertRejects(transform, json("{'tool_choice':{'type':'function','name':'" + HUGE + "'}}"));
    }
}
