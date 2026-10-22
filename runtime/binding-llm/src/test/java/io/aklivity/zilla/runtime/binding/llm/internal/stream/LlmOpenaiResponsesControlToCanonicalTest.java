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


    @Test
    public void shouldLiftTextFormatText()
    {
        assertTransforms(transform,
            json("{'text':{'format':{'type':'text'}}}"),
            json("{'x:response_format':{'type':'text'}}"));
    }

    @Test
    public void shouldLiftTextFormatJsonObject()
    {
        assertTransforms(transform,
            json("{'text':{'format':{'type':'json_object'}}}"),
            json("{'x:response_format':{'type':'json_object'}}"));
    }

    @Test
    public void shouldNestJsonSchemaFormat()
    {
        assertTransforms(transform,
            json("{'text':{'format':{'type':'json_schema','name':'answer','description':'d','schema':{'type':'object'}," +
                "'strict':true}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'answer','description':'d'," +
                "'schema':{'type':'object'},'strict':true}}}"));
    }

    @Test
    public void shouldNestJsonSchemaFormatWithTypeLast()
    {
        assertTransforms(transform,
            json("{'text':{'format':{'name':'answer','schema':{'type':'object'},'strict':true,'type':'json_schema'}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'answer','schema':{'type':'object'}," +
                "'strict':true}}}"));
    }

    @Test
    public void shouldStreamSchemaLargerThanWindowAfterType()
    {
        assertTransforms(transform,
            json("{'text':{'format':{'type':'json_schema','name':'a','schema':{'description':'" + HUGE + "'}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'a','schema':{'description':'" + HUGE +
                "'}}}}"));
    }

    @Test
    public void shouldRejectMembersBeforeTypeBeyondBound()
    {
        assertRejects(transform,
            json("{'text':{'format':{'name':'a','schema':{'description':'" + HUGE + "'},'type':'json_schema'}}}"));
    }

    @Test
    public void shouldKeepOtherTextMembersAsResidueAfterFormat()
    {
        assertTransforms(transform,
            json("{'text':{'format':{'type':'json_object'},'verbosity':'low'}}"),
            json("{'x:response_format':{'type':'json_object'},'openai:text':{'verbosity':'low'}}"));
    }

    @Test
    public void shouldKeepOtherTextMembersAsResidueBeforeFormat()
    {
        assertTransforms(transform,
            json("{'text':{'verbosity':'low','format':{'type':'json_object'}}}"),
            json("{'openai:text':{'verbosity':'low'},'x:response_format':{'type':'json_object'}}"));
    }

    @Test
    public void shouldSplitTextResidueAroundFormat()
    {
        assertTransforms(transform,
            json("{'text':{'verbosity':'low','format':{'type':'text'},'other':{'a':[1]}}}"),
            json("{'openai:text':{'verbosity':'low'},'x:response_format':{'type':'text'},'openai:text':{'other':{'a':[1]}}}"));
    }

    @Test
    public void shouldKeepTextWithoutFormatAsResidue()
    {
        assertTransforms(transform,
            json("{'text':{'verbosity':'low'}}"),
            json("{'openai:text':{'verbosity':'low'}}"));
    }

    @Test
    public void shouldKeepEmptyTextAsResidue()
    {
        assertTransforms(transform, json("{'text':{}}"), json("{'openai:text':{}}"));
    }

    @Test
    public void shouldKeepNullTextAsResidue()
    {
        assertTransforms(transform, json("{'text':null}"), json("{'openai:text':null}"));
    }

    @Test
    public void shouldRejectUnknownFormatType()
    {
        assertRejects(transform, json("{'text':{'format':{'type':'grammar'}}}"));
    }

    @Test
    public void shouldRejectFormatWithoutType()
    {
        assertRejects(transform, json("{'text':{'format':{'name':'a'}}}"));
    }

    @Test
    public void shouldRejectFormatThatIsNotAnObject()
    {
        assertRejects(transform, json("{'text':{'format':'text'}}"));
    }

    @Test
    public void shouldLiftReasoningEffort()
    {
        for (String effort : new String[] {"none", "minimal", "low", "medium", "high", "xhigh"})
        {
            assertTransforms(transform,
                json("{'reasoning':{'effort':'" + effort + "'}}"),
                json("{'x:reasoning_effort':'" + effort + "'}"));
        }
    }

    @Test
    public void shouldKeepReasoningSummaryAsResidueAfterEffort()
    {
        assertTransforms(transform,
            json("{'reasoning':{'effort':'low','summary':'auto'}}"),
            json("{'x:reasoning_effort':'low','openai:reasoning':{'summary':'auto'}}"));
    }

    @Test
    public void shouldKeepReasoningSummaryAsResidueBeforeEffort()
    {
        assertTransforms(transform,
            json("{'reasoning':{'summary':'auto','effort':'low'}}"),
            json("{'openai:reasoning':{'summary':'auto'},'x:reasoning_effort':'low'}"));
    }

    @Test
    public void shouldSplitReasoningResidueAroundEffort()
    {
        assertTransforms(transform,
            json("{'reasoning':{'summary':'auto','effort':'low','generate_summary':'concise'}}"),
            json("{'openai:reasoning':{'summary':'auto'},'x:reasoning_effort':'low'," +
                "'openai:reasoning':{'generate_summary':'concise'}}"));
    }

    @Test
    public void shouldKeepReasoningWithoutEffortAsResidue()
    {
        assertTransforms(transform,
            json("{'reasoning':{'summary':'auto'}}"),
            json("{'openai:reasoning':{'summary':'auto'}}"));
    }

    @Test
    public void shouldDropNullReasoningEffort()
    {
        assertTransforms(transform,
            json("{'reasoning':{'effort':null,'summary':'auto'}}"),
            json("{'openai:reasoning':{'summary':'auto'}}"));
    }

    @Test
    public void shouldKeepEmptyReasoningAsResidue()
    {
        assertTransforms(transform, json("{'reasoning':{}}"), json("{'openai:reasoning':{}}"));
    }

    @Test
    public void shouldKeepNullReasoningAsResidue()
    {
        assertTransforms(transform, json("{'reasoning':null}"), json("{'openai:reasoning':null}"));
    }

    @Test
    public void shouldPromoteServiceTier()
    {
        assertTransforms(transform,
            json("{'service_tier':'auto'}"),
            json("{'x:service_tier':'auto'}"));
        assertTransforms(transform,
            json("{'service_tier':'default'}"),
            json("{'x:service_tier':'default'}"));
        assertTransforms(transform,
            json("{'service_tier':'flex'}"),
            json("{'x:service_tier':'flex'}"));
        assertTransforms(transform,
            json("{'service_tier':'priority'}"),
            json("{'x:service_tier':'priority'}"));
    }

    @Test
    public void shouldKeepServiceTierOfOtherValuesPrefixed()
    {
        assertTransforms(transform,
            json("{'service_tier':'scale'}"),
            json("{'openai:service_tier':'scale'}"));
        assertTransforms(transform,
            json("{'service_tier':'fast'}"),
            json("{'openai:service_tier':'fast'}"));
        assertTransforms(transform,
            json("{'service_tier':7}"),
            json("{'openai:service_tier':7}"));
        assertTransforms(transform,
            json("{'service_tier':null}"),
            json("{'openai:service_tier':null}"));
        assertTransforms(transform,
            json("{'service_tier':'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'}"),
            json("{'openai:service_tier':'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'}"));
    }

    @Test
    public void shouldPromoteServiceTierWithEscapes()
    {
        assertTransforms(transform,
            "{\"service_tier\":\"au\\u0074o\"}",
            json("{'x:service_tier':'auto'}"));
    }

    @Test
    public void shouldKeepUserAndPromptCacheKeyPrefixed()
    {
        assertTransforms(transform,
            json("{'user':'u','prompt_cache_key':'k','safety_identifier':'s'}"),
            json("{'openai:user':'u','openai:prompt_cache_key':'k','openai:safety_identifier':'s'}"));
    }
}
