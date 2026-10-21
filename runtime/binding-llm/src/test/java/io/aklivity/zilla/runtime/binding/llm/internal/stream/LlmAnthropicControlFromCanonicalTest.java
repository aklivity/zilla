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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmAnthropicControlFromCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmAnthropicControlFromCanonical transform = new LlmAnthropicControlFromCanonical(true);
    private final LlmAnthropicControlFromCanonical foreign = new LlmAnthropicControlFromCanonical(false);
    private final LlmAnthropicControlFromCanonical sameMembers = LlmAnthropicControlFromCanonical.members(true);
    private final LlmAnthropicControlFromCanonical foreignMembers = LlmAnthropicControlFromCanonical.members(false);

    @Test
    public void shouldRestoreOwnPrefixedMember()
    {
        assertTransforms(transform, json("{'anthropic:temperature':0.7}"), "0.7");

        assertThat(transform.member(), equalTo("temperature"));
    }

    @Test
    public void shouldRestoreObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'anthropic:metadata':{'user_id':'u','a':[1,{'b':'" + LONG + "'}],'openai:c':true}}"),
            json("{'user_id':'u','a':[1,{'b':'" + LONG + "'}],'openai:c':true}"));

        assertThat(transform.member(), equalTo("metadata"));
    }

    @Test
    public void shouldRestoreValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'anthropic:metadata':{'note':'" + HUGE + "'}}"),
            json("{'note':'" + HUGE + "'}"));

        assertThat(transform.member(), equalTo("metadata"));
    }

    @Test
    public void shouldDropOwnPrefixedMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'anthropic:temperature':0.7}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldDropOwnPrefixedObjectMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'anthropic:metadata':{'a':[1,{'b':'" + LONG + "'}]}}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldPassUnprefixedMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'temperature':0.2}"), "0.2");

        assertThat(foreign.member(), equalTo("temperature"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMember()
    {
        assertTransforms(transform, json("{'openai:logit_bias':{'a':1}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldDropOtherDialectPrefixedObjectMember()
    {
        assertTransforms(transform, json("{'openai:metadata':{'a':[1,{'b':'" + LONG + "'}]}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldPassUnprefixedMember()
    {
        assertTransforms(transform, json("{'temperature':0.2}"), "0.2");

        assertThat(transform.member(), equalTo("temperature"));
    }

    @Test
    public void shouldPassStream()
    {
        assertTransforms(transform, json("{'stream':true}"), "true");

        assertThat(transform.member(), equalTo("stream"));
    }

    @Test
    public void shouldDropStreamOptions()
    {
        assertTransforms(transform, json("{'stream_options':{'include_usage':true,'openai:include_obfuscation':false}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldRejectMemberThatIsNotAnObject()
    {
        assertRejects(transform, json("[1]"));
    }


    @Test
    public void shouldRestoreSharedSamplingMembers()
    {
        assertTransforms(foreignMembers,
            json("{'x:temperature':0.7,'x:top_p':0.9,'x:top_k':40,'x:stop':['END','STOP']}"),
            json("{'temperature':0.7,'top_p':0.9,'top_k':40,'stop_sequences':['END','STOP']}"));

        assertThat(foreignMembers.member(), equalTo("temperature"));
    }

    @Test
    public void shouldClampSharedTemperatureToMaximum()
    {
        assertTransforms(foreignMembers, json("{'x:temperature':1.5}"), json("{'temperature':1.0}"));
    }

    @Test
    public void shouldClampSharedTemperatureToMinimum()
    {
        assertTransforms(foreignMembers, json("{'x:temperature':-0.5}"), json("{'temperature':0.0}"));
    }

    @Test
    public void shouldKeepSharedTemperatureWithinRangeVerbatim()
    {
        assertTransforms(foreignMembers, json("{'x:temperature':1}"), json("{'temperature':1}"));
        assertTransforms(foreignMembers, json("{'x:temperature':0.30}"), json("{'temperature':0.30}"));
    }

    @Test
    public void shouldRejectSharedTemperatureThatIsNotANumber()
    {
        assertRejects(foreignMembers, json("{'x:temperature':'hot'}"));
    }

    @Test
    public void shouldDropSharedMemberNotSupported()
    {
        assertTransforms(foreignMembers,
            json("{'x:seed':1,'x:frequency_penalty':0.5,'x:presence_penalty':0.5,'x:top_k':3}"),
            json("{'top_k':3}"));
    }

    @Test
    public void shouldDropSharedStopThatIsNull()
    {
        assertTransforms(foreignMembers, json("{'x:stop':null,'x:top_k':3}"), json("{'top_k':3}"));
    }

    @Test
    public void shouldDropSharedMemberWhenExtracting()
    {
        assertTransforms(foreign, json("{'x:temperature':0.7}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldRestoreToolChoiceAutoAsFragment()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'auto'}"),
            json("{'tool_choice':{'type':'auto'}}"));

        assertThat(foreignMembers.choiceType(), equalTo("auto"));
    }

    @Test
    public void shouldRestoreToolChoiceRequiredAsFragment()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'required'}"),
            json("{'tool_choice':{'type':'any'}}"));

        assertThat(foreignMembers.choiceType(), equalTo("any"));
    }

    @Test
    public void shouldRestoreToolChoiceNoneAsFragment()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'none'}"),
            json("{'tool_choice':{'type':'none'}}"));

        assertThat(foreignMembers.choiceType(), equalTo("none"));
    }

    @Test
    public void shouldRestoreFunctionToolChoiceAsTool()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"),
            json("{'tool_choice':{'type':'tool','name':'get_weather'}}"));

        assertThat(foreignMembers.choiceType(), equalTo("tool"));
    }

    @Test
    public void shouldRejectAllowedTools()
    {
        assertRejects(foreignMembers, json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'auto'," +
            "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
            "'function':{'name':'b'}}]}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("tool_choice"));
    }

    @Test
    public void shouldRejectUnrecognizedToolChoice()
    {
        assertRejects(foreignMembers, json("{'x:tool_choice':'bogus'}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("tool_choice"));
    }

    @Test
    public void shouldHoldParallelToolCallsDisabled()
    {
        assertTransforms(foreignMembers,
            json("{'x:parallel_tool_calls':false}"),
            json("{}"));

        assertThat(foreignMembers.parallelCalls(), equalTo(LlmAnthropicControlFromCanonical.PARALLEL_DISABLED));
        assertThat(foreignMembers.choiceType(), nullValue());
    }

    @Test
    public void shouldHoldParallelToolCallsEnabled()
    {
        assertTransforms(foreignMembers,
            json("{'x:parallel_tool_calls':true}"),
            json("{}"));

        assertThat(foreignMembers.parallelCalls(), equalTo(LlmAnthropicControlFromCanonical.PARALLEL_ENABLED));
    }

    @Test
    public void shouldIgnoreNullParallelToolCalls()
    {
        assertTransforms(foreignMembers,
            json("{'x:parallel_tool_calls':null}"),
            json("{}"));

        assertThat(foreignMembers.parallelCalls(), equalTo(LlmAnthropicControlFromCanonical.PARALLEL_UNSET));
    }

    @Test
    public void shouldRejectNonBooleanParallelToolCalls()
    {
        assertRejects(foreignMembers, json("{'x:parallel_tool_calls':'yes'}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("parallel_tool_calls"));
    }

    @Test
    public void shouldHoldBothToolChoiceAndParallelToolCalls()
    {
        assertTransforms(foreignMembers,
            json("{'x:parallel_tool_calls':false,'x:tool_choice':'required'}"),
            json("{'tool_choice':{'type':'any'}}"));

        assertThat(foreignMembers.parallelCalls(), equalTo(LlmAnthropicControlFromCanonical.PARALLEL_DISABLED));
        assertThat(foreignMembers.choiceType(), equalTo("any"));
    }

    @Test
    public void shouldRestoreResidueAsFragment()
    {
        assertTransforms(sameMembers,
            json("{'anthropic:tool_choice':{'extra':1}}"),
            json("{'tool_choice':{'extra':1}}"));
    }


    @Test
    public void shouldWriteJsonSchemaFormatAsOutputConfig()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'answer','description':'d'," +
                "'schema':{'type':'object','a':[1,'" + LONG + "']},'strict':true}}}"),
            json("{'output_config':{'format':{'type':'json_schema','schema':{'type':'object','a':[1,'" + LONG + "']}}}}"));

        assertThat(foreignMembers.member(), equalTo("output_config"));
    }

    @Test
    public void shouldWriteJsonSchemaFormatWithTypeLast()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'json_schema':{'name':'a','schema':{'x':1}},'type':'json_schema'}}"),
            json("{'output_config':{'format':{'type':'json_schema','schema':{'x':1}}}}"));
    }

    @Test
    public void shouldWriteSchemaLargerThanWindow()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'a','schema':{'d':'" + HUGE + "'}}}}"),
            json("{'output_config':{'format':{'type':'json_schema','schema':{'d':'" + HUGE + "'}}}}"));
    }

    @Test
    public void shouldOmitTextFormat()
    {
        assertTransforms(foreignMembers, json("{'x:response_format':{'type':'text'}}"), json("{}"));
    }

    @Test
    public void shouldDropNullFormat()
    {
        assertTransforms(foreignMembers, json("{'x:response_format':null}"), json("{}"));
    }

    @Test
    public void shouldRejectJsonObjectFormat()
    {
        assertRejects(foreignMembers, json("{'x:response_format':{'type':'json_object'}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("response_format"));
    }

    @Test
    public void shouldRejectUnknownFormatType()
    {
        assertRejects(foreignMembers, json("{'x:response_format':{'type':'grammar'}}"));
    }

    @Test
    public void shouldRejectDeniedSchemaKeyword()
    {
        assertRejects(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'a','schema':{'type':'object'," +
                "'properties':{'n':{'type':'integer','minimum':1}}}}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("response_format keyword minimum"));
    }

    @Test
    public void shouldRejectDeniedSchemaKeywordLargerThanWindow()
    {
        assertRejects(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'a','schema':{'d':'" + HUGE +
                "','maxLength':3}}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("response_format keyword maxLength"));
    }

    @Test
    public void shouldRejectExternalReference()
    {
        assertRejects(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'schema':{'$ref':'https://e.com/s'}}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("response_format keyword $ref"));
    }

    @Test
    public void shouldRejectMinItemsAboveOne()
    {
        assertRejects(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'schema':{'minItems':2}}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("response_format keyword minItems"));
    }

    @Test
    public void shouldAcceptKeywordsAsPropertyNames()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'schema':{'properties':{'minimum':{}," +
                "'maxItems':{'minItems':1,'enum':[{'maximum':1}]}}}}}}"),
            json("{'output_config':{'format':{'type':'json_schema','schema':{'properties':{'minimum':{}," +
                "'maxItems':{'minItems':1,'enum':[{'maximum':1}]}}}}}}"));
    }

    @Test
    public void shouldNotScanAcrossRequests()
    {
        assertRejects(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'schema':{'maxLength':1}}}}"));
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'schema':{'type':'string'}}}}"),
            json("{'output_config':{'format':{'type':'json_schema','schema':{'type':'string'}}}}"));
    }
}
