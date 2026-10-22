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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.Test;

public class LlmAnthropicControlToCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmAnthropicControlToCanonical transform = new LlmAnthropicControlToCanonical();

    @Test
    public void shouldPrefixScalarMember()
    {
        assertTransforms(transform,
            json("{'temperature':0.7}"),
            json("{'anthropic:temperature':0.7}"));
    }

    @Test
    public void shouldPrefixStringMember()
    {
        assertTransforms(transform,
            json("{'stop_sequence':'" + LONG + "'}"),
            json("{'anthropic:stop_sequence':'" + LONG + "'}"));
    }

    @Test
    public void shouldPrefixNullMember()
    {
        assertTransforms(transform,
            json("{'stop_sequence':null}"),
            json("{'anthropic:stop_sequence':null}"));
    }

    @Test
    public void shouldMoveObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'context_management':{'user_id':'u','a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}"),
            json("{'anthropic:context_management':{'user_id':'u','a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}"));
    }

    @Test
    public void shouldMoveArrayValueUnchanged()
    {
        assertTransforms(transform,
            json("{'stop_sequences':['a','b',{'c':1}]}"),
            json("{'anthropic:stop_sequences':['a','b',{'c':1}]}"));
    }

    @Test
    public void shouldMoveValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'mcp_servers':[{'note':'" + HUGE + "'}]}"),
            json("{'anthropic:mcp_servers':[{'note':'" + HUGE + "'}]}"));
    }

    @Test
    public void shouldPrefixStreamOptionsAsNativeMember()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':false}}"),
            json("{'anthropic:stream_options':{'include_usage':false}}"));

        assertThat(transform.streamSeen(), is(false));
    }

    @Test
    public void shouldCarryStreamUnprefixed()
    {
        assertTransforms(transform, json("{'stream':true}"), json("{'stream':true}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(true));
    }

    @Test
    public void shouldCarryStreamFalse()
    {
        assertTransforms(transform, json("{'stream':false}"), json("{'stream':false}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldCarryStreamNull()
    {
        assertTransforms(transform, json("{'stream':null}"), json("{'stream':null}"));

        assertThat(transform.streamSeen(), is(true));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldResetStreamState()
    {
        assertTransforms(transform, json("{'stream':true}"), json("{'stream':true}"));
        assertTransforms(transform, json("{'temperature':1}"), json("{'anthropic:temperature':1}"));

        assertThat(transform.streamSeen(), is(false));
        assertThat(transform.streaming(), is(false));
    }

    @Test
    public void shouldRejectStreamThatIsNotBoolean()
    {
        assertRejects(transform, json("{'stream':'yes'}"));
        assertRejects(transform, json("{'stream':{}}"));
    }

    @Test
    public void shouldPromoteToolChoiceAuto()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto'}}"),
            json("{'x:tool_choice':'auto'}"));
    }

    @Test
    public void shouldPromoteToolChoiceAny()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'any'}}"),
            json("{'x:tool_choice':'required'}"));
    }

    @Test
    public void shouldPromoteToolChoiceNone()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'none'}}"),
            json("{'x:tool_choice':'none'}"));
    }

    @Test
    public void shouldPromoteToolAsFunction()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'tool','name':'get_weather'}}"),
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"));
    }

    @Test
    public void shouldSplitDisabledParallelUseAsNegatedParallelToolCalls()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto','disable_parallel_tool_use':true}}"),
            json("{'x:tool_choice':'auto','x:parallel_tool_calls':false}"));
    }

    @Test
    public void shouldSplitEnabledParallelUseAsParallelToolCalls()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'disable_parallel_tool_use':false,'type':'any'}}"),
            json("{'x:tool_choice':'required','x:parallel_tool_calls':true}"));
    }

    @Test
    public void shouldPromoteParallelUseWithoutType()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'disable_parallel_tool_use':true}}"),
            json("{'x:parallel_tool_calls':false}"));
    }

    @Test
    public void shouldKeepOtherMembersAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto','extra':[1,{'b':'" + LONG + "'}]}}"),
            json("{'x:tool_choice':'auto','anthropic:tool_choice':{'extra':[1,{'b':'" + LONG + "'}]}}"));
    }

    @Test
    public void shouldKeepUnknownTypeAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'future','name':'f','disable_parallel_tool_use':true}}"),
            json("{'x:parallel_tool_calls':false,'anthropic:tool_choice':{'type':'future','name':'f'}}"));
    }

    @Test
    public void shouldKeepToolWithoutNameAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'tool'}}"),
            json("{'anthropic:tool_choice':{'type':'tool'}}"));
    }

    @Test
    public void shouldKeepNonBooleanParallelUseAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{'type':'auto','disable_parallel_tool_use':'yes'}}"),
            json("{'x:tool_choice':'auto','anthropic:tool_choice':{'disable_parallel_tool_use':'yes'}}"));
    }

    @Test
    public void shouldKeepEmptyObjectAsResidue()
    {
        assertTransforms(transform,
            json("{'tool_choice':{}}"),
            json("{'anthropic:tool_choice':{}}"));
    }

    @Test
    public void shouldKeepNonObjectToolChoiceAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':'auto'}"),
            json("{'anthropic:tool_choice':'auto'}"));
    }

    @Test
    public void shouldKeepNullToolChoiceAsNative()
    {
        assertTransforms(transform,
            json("{'tool_choice':null}"),
            json("{'anthropic:tool_choice':null}"));
    }

    @Test
    public void shouldRejectToolChoiceLargerThanTheCaptureLimit()
    {
        assertRejects(transform, json("{'tool_choice':{'type':'future','name':'" + HUGE + "'}}"));
    }


    @Test
    public void shouldLiftOutputConfigFormat()
    {
        assertTransforms(transform,
            json("{'output_config':{'format':{'type':'json_schema','schema':{'type':'object'}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{'type':'object'}}}}"));
    }

    @Test
    public void shouldLiftOutputConfigFormatWithTypeLast()
    {
        assertTransforms(transform,
            json("{'output_config':{'format':{'schema':{'type':'object'},'type':'json_schema'}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{'type':'object'}}}}"));
    }

    @Test
    public void shouldStreamSchemaLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'output_config':{'format':{'type':'json_schema','schema':{'description':'" + HUGE + "'}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{'description':'" +
                HUGE + "'}}}}"));
    }

    @Test
    public void shouldLiftEffortBeforeFormat()
    {
        assertTransforms(transform,
            json("{'output_config':{'effort':'low','format':{'type':'json_schema','schema':{}}}}"),
            json("{'x:reasoning_effort':'low','x:response_format':{'type':'json_schema'," +
                "'json_schema':{'name':'response','schema':{}}}}"));
    }

    @Test
    public void shouldLiftEffortAfterFormat()
    {
        assertTransforms(transform,
            json("{'output_config':{'format':{'type':'json_schema','schema':{}},'effort':'low'}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{}}}," +
                "'x:reasoning_effort':'low'}"));
    }

    @Test
    public void shouldSplitOutputConfigResidueAroundFormatAndEffort()
    {
        assertTransforms(transform,
            json("{'output_config':{'first':1,'effort':'high','format':{'type':'json_schema','schema':{}},'other':true}}"),
            json("{'anthropic:output_config':{'first':1},'x:reasoning_effort':'high','x:response_format':" +
                "{'type':'json_schema','json_schema':{'name':'response','schema':{}}}," +
                "'anthropic:output_config':{'other':true}}"));
    }

    @Test
    public void shouldLiftEveryEffort()
    {
        for (String effort : new String[] {"low", "medium", "high", "xhigh", "max"})
        {
            assertTransforms(transform,
                json("{'output_config':{'effort':'" + effort + "'}}"),
                json("{'x:reasoning_effort':'" + effort + "'}"));
        }
    }

    @Test
    public void shouldDropNullEffort()
    {
        assertTransforms(transform,
            json("{'output_config':{'effort':null,'other':true}}"),
            json("{'anthropic:output_config':{'other':true}}"));
    }

    @Test
    public void shouldKeepOutputConfigWithoutFormatOrEffortAsResidue()
    {
        assertTransforms(transform,
            json("{'output_config':{'other':true}}"),
            json("{'anthropic:output_config':{'other':true}}"));
    }

    @Test
    public void shouldLiftDisabledThinkingAsNone()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'disabled'}}"),
            json("{'x:reasoning_effort':'none'}"));
    }

    @Test
    public void shouldLiftDisabledThinkingAndKeepOtherMembersAsResidue()
    {
        assertTransforms(transform,
            json("{'thinking':{'display':'omitted','type':'disabled','other':1}}"),
            json("{'anthropic:thinking':{'display':'omitted'},'x:reasoning_effort':'none','anthropic:thinking':{'other':1}}"));
    }

    @Test
    public void shouldLiftThinkingBudgetAndConsumeEnabledType()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'enabled','budget_tokens':2048}}"),
            json("{'x:thinking_budget_tokens':2048}"));
    }

    @Test
    public void shouldLiftThinkingBudgetBeforeEnabledType()
    {
        assertTransforms(transform,
            json("{'thinking':{'budget_tokens':2048,'type':'enabled'}}"),
            json("{'x:thinking_budget_tokens':2048}"));
    }

    @Test
    public void shouldKeepThinkingResidueAroundBudget()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'enabled','budget_tokens':2048,'display':'omitted'}}"),
            json("{'x:thinking_budget_tokens':2048,'anthropic:thinking':{'display':'omitted'}}"));
    }

    @Test
    public void shouldKeepEnabledThinkingWithoutBudgetAsResidue()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'enabled'}}"),
            json("{'anthropic:thinking':{'type':'enabled'}}"));
    }

    @Test
    public void shouldKeepEnabledThinkingAfterOtherMembersWithoutBudgetAsResidue()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'enabled','display':'omitted'}}"),
            json("{'anthropic:thinking':{'display':'omitted','type':'enabled'}}"));
    }

    @Test
    public void shouldKeepAdaptiveThinkingAsResidue()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'adaptive','display':'summarized'}}"),
            json("{'anthropic:thinking':{'type':'adaptive','display':'summarized'}}"));
    }

    @Test
    public void shouldDropNullThinkingBudget()
    {
        assertTransforms(transform,
            json("{'thinking':{'type':'enabled','budget_tokens':null}}"),
            json("{}"));
    }

    @Test
    public void shouldKeepEmptyThinkingAsResidue()
    {
        assertTransforms(transform, json("{'thinking':{}}"), json("{'anthropic:thinking':{}}"));
    }

    @Test
    public void shouldKeepNullThinkingAsResidue()
    {
        assertTransforms(transform, json("{'thinking':null}"), json("{'anthropic:thinking':null}"));
    }

    @Test
    public void shouldRejectThinkingBudgetThatIsNotANumber()
    {
        assertRejects(transform, json("{'thinking':{'budget_tokens':'high'}}"));
    }

    @Test
    public void shouldKeepEmptyOutputConfigAsResidue()
    {
        assertTransforms(transform, json("{'output_config':{}}"), json("{'anthropic:output_config':{}}"));
    }

    @Test
    public void shouldKeepNullOutputConfigAsResidue()
    {
        assertTransforms(transform, json("{'output_config':null}"), json("{'anthropic:output_config':null}"));
    }

    @Test
    public void shouldRejectFormatTypeThatIsNotJsonSchema()
    {
        assertRejects(transform, json("{'output_config':{'format':{'type':'text'}}}"));
    }

    @Test
    public void shouldRejectFormatThatIsNotAnObject()
    {
        assertRejects(transform, json("{'output_config':{'format':'json_schema'}}"));
    }

    @Test
    public void shouldPromoteServiceTier()
    {
        assertTransforms(transform,
            json("{'service_tier':'auto'}"),
            json("{'x:service_tier':'auto'}"));
        assertTransforms(transform,
            json("{'service_tier':'standard_only'}"),
            json("{'x:service_tier':'default'}"));
    }

    @Test
    public void shouldKeepServiceTierOfOtherValuesPrefixed()
    {
        assertTransforms(transform,
            json("{'service_tier':'other'}"),
            json("{'anthropic:service_tier':'other'}"));
        assertTransforms(transform,
            json("{'service_tier':'priority'}"),
            json("{'anthropic:service_tier':'priority'}"));
        assertTransforms(transform,
            json("{'service_tier':7}"),
            json("{'anthropic:service_tier':7}"));
        assertTransforms(transform,
            json("{'service_tier':null}"),
            json("{'anthropic:service_tier':null}"));
    }

    @Test
    public void shouldPromoteServiceTierWithEscapes()
    {
        assertTransforms(transform,
            "{\"service_tier\":\"au\\u0074o\"}",
            json("{'x:service_tier':'auto'}"));
    }

    @Test
    public void shouldLiftMetadataUser()
    {
        assertTransforms(transform,
            json("{'metadata':{'user_id':'u-1'}}"),
            json("{'x:user':'u-1'}"));
    }

    @Test
    public void shouldLiftMetadataUserBetweenResidue()
    {
        assertTransforms(transform,
            json("{'metadata':{'a':1,'user_id':'u-1','b':{'c':[2]}}}"),
            json("{'anthropic:metadata':{'a':1},'x:user':'u-1','anthropic:metadata':{'b':{'c':[2]}}}"));
    }

    @Test
    public void shouldLiftMetadataUserFirstWithResidue()
    {
        assertTransforms(transform,
            json("{'metadata':{'user_id':'u-1','a':1}}"),
            json("{'x:user':'u-1','anthropic:metadata':{'a':1}}"));
    }

    @Test
    public void shouldLiftMetadataUserLastWithResidue()
    {
        assertTransforms(transform,
            json("{'metadata':{'a':1,'user_id':'u-1'}}"),
            json("{'anthropic:metadata':{'a':1},'x:user':'u-1'}"));
    }

    @Test
    public void shouldLiftMetadataUserWithEscapes()
    {
        assertTransforms(transform,
            "{\"metadata\":{\"user_id\":\"a\\\"b\\u00e9\"}}",
            "{\"x:user\":\"a\\\"bé\"}");
    }

    @Test
    public void shouldDropMetadataUserThatIsNotString()
    {
        assertTransforms(transform,
            json("{'metadata':{'user_id':null,'a':1}}"),
            json("{'anthropic:metadata':{'a':1}}"));
        assertTransforms(transform,
            json("{'metadata':{'user_id':7}}"),
            json("{}"));
    }

    @Test
    public void shouldKeepMetadataWithoutUserAsResidue()
    {
        assertTransforms(transform,
            json("{'metadata':{'a':1}}"),
            json("{'anthropic:metadata':{'a':1}}"));
    }

    @Test
    public void shouldKeepEmptyMetadataAsResidue()
    {
        assertTransforms(transform,
            json("{'metadata':{}}"),
            json("{'anthropic:metadata':{}}"));
    }

    @Test
    public void shouldKeepNullMetadataAsResidue()
    {
        assertTransforms(transform,
            json("{'metadata':null}"),
            json("{'anthropic:metadata':null}"));
    }
}
