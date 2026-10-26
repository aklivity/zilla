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

import java.util.List;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.config.LlmEffort;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier;

public class LlmOpenaiControlFromCanonicalTest
{
    private static final String CCS = "openai/ccs";
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);
    private static final String AT_LIMIT = "0123456789".repeat(13).substring(0, 128);
    private static final String OVER_LIMIT = AT_LIMIT + "x";
    private static final String SUPPLEMENTARY_AT_LIMIT = "\ud83d\ude00".repeat(128);
    private static final String SUPPLEMENTARY_OVER_LIMIT = SUPPLEMENTARY_AT_LIMIT + "\ud83d\ude00";

    private final LlmOpenaiControlFromCanonical transform = new LlmOpenaiControlFromCanonical(CCS, true);
    private final LlmOpenaiControlFromCanonical foreign = new LlmOpenaiControlFromCanonical(CCS, false);
    private final LlmOpenaiControlFromCanonical sameMembers = LlmOpenaiControlFromCanonical.members(CCS, true);
    private final LlmOpenaiControlFromCanonical foreignMembers = LlmOpenaiControlFromCanonical.members(CCS, false);

    @Test
    public void shouldRestoreOwnPrefixedMember()
    {
        assertTransforms(transform, json("{'openai/ccs:temperature':0.7}"), "0.7");

        assertThat(transform.member(), equalTo("temperature"));
    }

    @Test
    public void shouldRestoreVendorPrefixedMember()
    {
        assertTransforms(transform, json("{'openai:store':false}"), "false");

        assertThat(transform.member(), equalTo("store"));
    }

    @Test
    public void shouldRestoreVendorPrefixedMemberAcrossVariants()
    {
        assertTransforms(foreign, json("{'openai:store':false}"), "false");

        assertThat(foreign.member(), equalTo("store"));
    }

    @Test
    public void shouldRestoreObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'openai/ccs:response_format':{'type':'json_schema','json_schema':{'name':'x','schema':{'a':[1,{'b':'" + LONG +
                "'}],'anthropic:c':true}}}}"),
            json("{'type':'json_schema','json_schema':{'name':'x','schema':{'a':[1,{'b':'" + LONG +
                "'}],'anthropic:c':true}}}"));

        assertThat(transform.member(), equalTo("response_format"));
    }

    @Test
    public void shouldRestoreValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'openai:metadata':{'note':'" + HUGE + "'}}"),
            json("{'note':'" + HUGE + "'}"));

        assertThat(transform.member(), equalTo("metadata"));
    }

    @Test
    public void shouldDropSiblingVariantPrefixedMember()
    {
        assertTransforms(transform, json("{'openai/rs:truncation':'auto'}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldDropSiblingVariantPrefixedMemberAcrossVariants()
    {
        assertTransforms(foreign, json("{'openai/rs:truncation':'auto'}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldDropSiblingVariantPrefixedObjectMember()
    {
        assertTransforms(foreign, json("{'openai/rs:text':{'a':[1,{'b':'" + LONG + "'}]}}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldDropOtherVendorVariantPrefixedMember()
    {
        assertTransforms(transform, json("{'anthropic/messages:top_k':5}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldDropMemberPrefixedWithPrefixOfOwnPrefix()
    {
        assertTransforms(transform, json("{'openai/cc:temperature':0.7}"), "null");

        assertThat(transform.member(), nullValue());
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
        assertTransforms(transform, json("{'anthropic:top_k':5}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldDropOtherDialectPrefixedObjectMember()
    {
        assertTransforms(transform, json("{'anthropic:metadata':{'a':[1,{'b':'" + LONG + "'}]}}"), "null");

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
    public void shouldPassStreamOptions()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':true}}"),
            json("{'include_usage':true}"));

        assertThat(transform.member(), equalTo("stream_options"));
    }

    @Test
    public void shouldRestoreNativeOptionMembersAndDropForeign()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':true,'openai:include_obfuscation':false," +
                "'anthropic:other':{'a':[1,'" + LONG + "']},'openai/ccs:extra':{'a':[1]}}}"),
            json("{'include_usage':true,'include_obfuscation':false,'extra':{'a':[1]}}"));

        assertThat(transform.member(), equalTo("stream_options"));
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
            json("{'x:temperature':0.7,'x:top_p':0.9,'x:seed':42,'x:frequency_penalty':0.5,'x:presence_penalty':-0.5}"),
            json("{'temperature':0.7,'top_p':0.9,'seed':42,'frequency_penalty':0.5,'presence_penalty':-0.5}"));
    }

    @Test
    public void shouldRestoreSharedStopEntries()
    {
        assertTransforms(foreignMembers,
            json("{'x:stop':['a','b','c','d']}"),
            json("{'stop':['a','b','c','d']}"));
    }

    @Test
    public void shouldRestoreSharedStopEntriesBeyondLimitSameDialect()
    {
        assertTransforms(sameMembers,
            json("{'x:stop':['a','b','c','d','e']}"),
            json("{'stop':['a','b','c','d','e']}"));
    }

    @Test
    public void shouldRejectSharedStopWithMoreEntriesThanLimit()
    {
        assertRejects(foreignMembers, json("{'x:stop':['a','b','c','d','e']}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("stop"));
    }

    @Test
    public void shouldDropSharedStopThatIsNull()
    {
        assertTransforms(foreignMembers, json("{'x:stop':null,'x:seed':1}"), json("{'seed':1}"));
    }

    @Test
    public void shouldRejectSharedStopThatIsNotAnArray()
    {
        assertRejects(foreignMembers, json("{'x:stop':'a'}"));
    }

    @Test
    public void shouldDropSharedMemberNotSupported()
    {
        assertTransforms(foreignMembers, json("{'x:top_k':40,'x:temperature':0.7}"), json("{'temperature':0.7}"));
    }

    @Test
    public void shouldDropSharedMemberWhenExtracting()
    {
        assertTransforms(foreign, json("{'x:temperature':0.7}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldPassSharedTemperatureOutOfRange()
    {
        assertTransforms(foreignMembers, json("{'x:temperature':99}"), json("{'temperature':99}"));
    }

    @Test
    public void shouldRestoreToolChoiceAndParallelToolCallsUnchanged()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'auto','x:parallel_tool_calls':false}"),
            json("{'tool_choice':'auto','parallel_tool_calls':false}"));
    }

    @Test
    public void shouldRestoreToolChoiceObjectUnchanged()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"),
            json("{'tool_choice':{'type':'function','function':{'name':'get_weather'}}}"));
    }

    @Test
    public void shouldRestoreAllowedToolsUnchanged()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'auto'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"),
            json("{'tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'auto'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"));
    }

    @Test
    public void shouldRecordReasoningEffort()
    {
        for (String effort : new String[] {"none", "minimal", "low", "medium", "high", "xhigh", "max"})
        {
            assertTransforms(foreignMembers, json("{'x:reasoning_effort':'" + effort + "'}"), json("{}"));

            assertThat(foreignMembers.effort(), equalTo(LlmEffort.of(effort)));
        }
    }

    @Test
    public void shouldIgnoreNullReasoningEffort()
    {
        assertTransforms(foreignMembers, json("{'x:reasoning_effort':null}"), json("{}"));

        assertThat(foreignMembers.effort(), equalTo(LlmEffort.ABSENT));
    }

    @Test
    public void shouldRejectUnknownReasoningEffort()
    {
        assertRejects(foreignMembers, json("{'x:reasoning_effort':'turbo'}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("reasoning_effort"));
    }

    @Test
    public void shouldRejectReasoningEffortThatIsNotAString()
    {
        assertRejects(foreignMembers, json("{'x:reasoning_effort':3}"));
    }

    @Test
    public void shouldRecordThinkingBudget()
    {
        assertTransforms(foreignMembers, json("{'x:thinking_budget_tokens':4096}"), json("{}"));

        assertThat(foreignMembers.budget(), equalTo(4096));
    }

    @Test
    public void shouldRecordZeroThinkingBudget()
    {
        assertTransforms(foreignMembers, json("{'x:thinking_budget_tokens':0}"), json("{}"));

        assertThat(foreignMembers.budget(), equalTo(0));
    }

    @Test
    public void shouldIgnoreNullThinkingBudget()
    {
        assertTransforms(foreignMembers, json("{'x:thinking_budget_tokens':null}"), json("{}"));

        assertThat(foreignMembers.budget(), equalTo(LlmEffort.ABSENT));
    }

    @Test
    public void shouldRejectNegativeThinkingBudget()
    {
        assertRejects(foreignMembers, json("{'x:thinking_budget_tokens':-1}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("thinking_budget_tokens"));
    }

    @Test
    public void shouldRejectFractionalThinkingBudget()
    {
        assertRejects(foreignMembers, json("{'x:thinking_budget_tokens':1.5}"));
    }

    @Test
    public void shouldRejectThinkingBudgetThatIsNotANumber()
    {
        assertRejects(foreignMembers, json("{'x:thinking_budget_tokens':'high'}"));
    }

    @Test
    public void shouldDropReasoningMembersWhenExtracting()
    {
        final LlmOpenaiControlFromCanonical extracting = new LlmOpenaiControlFromCanonical(CCS, false);

        assertTransforms(extracting, json("{'x:reasoning_effort':'low','x:thinking_budget_tokens':2048}"), "null");
    }

    @Test
    public void shouldDropSamplingWhenModelRejectsSampling()
    {
        final LlmOpenaiControlFromCanonical rejecting = LlmOpenaiControlFromCanonical.members(CCS, false,
            new LlmModelCapability(false, null, -1, -1, null));

        assertTransforms(rejecting,
            json("{'x:temperature':0.7,'x:top_p':0.9,'x:seed':42}"),
            json("{'seed':42}"));
    }

    @Test
    public void shouldKeepSamplingWhenModelAcceptsSampling()
    {
        final LlmOpenaiControlFromCanonical accepting = LlmOpenaiControlFromCanonical.members(CCS, false,
            new LlmModelCapability(true, null, -1, -1, null));

        assertTransforms(accepting,
            json("{'x:temperature':0.7,'x:top_p':0.9,'x:seed':42}"),
            json("{'temperature':0.7,'top_p':0.9,'seed':42}"));
    }

    @Test
    public void shouldRestoreUser()
    {
        assertTransforms(foreignMembers,
            json("{'x:user':'u-1'}"),
            json("{'safety_identifier':'u-1'}"));
    }

    @Test
    public void shouldRestoreUserAtLimit()
    {
        assertTransforms(foreignMembers,
            json("{'x:user':'" + AT_LIMIT + "'}"),
            json("{'safety_identifier':'" + AT_LIMIT + "'}"));
    }

    @Test
    public void shouldDropUserOverLimit()
    {
        assertTransforms(foreignMembers,
            json("{'x:user':'" + OVER_LIMIT + "','x:top_p':0.5}"),
            json("{'top_p':0.5}"));
    }

    @Test
    public void shouldCountUserLimitInCodePoints()
    {
        assertTransforms(foreignMembers,
            json("{'x:user':'" + SUPPLEMENTARY_AT_LIMIT + "'}"),
            json("{'safety_identifier':'" + SUPPLEMENTARY_AT_LIMIT + "'}"));
        assertTransforms(foreignMembers,
            json("{'x:user':'" + SUPPLEMENTARY_OVER_LIMIT + "'}"),
            json("{}"));
    }

    @Test
    public void shouldRestoreUserWithEscapes()
    {
        assertTransforms(foreignMembers,
            "{\"x:user\":\"a\\\"b\\\\c\\u00e9\"}",
            "{\"safety_identifier\":\"a\\\"b\\\\c\u00e9\"}");
    }

    @Test
    public void shouldDropUserThatIsNotString()
    {
        assertTransforms(foreignMembers, json("{'x:user':7}"), json("{}"));
        assertTransforms(foreignMembers, json("{'x:user':{'a':[1]}}"), json("{}"));
        assertTransforms(foreignMembers, json("{'x:user':null}"), json("{}"));
    }

    @Test
    public void shouldDropUserLargerThanHeldBound()
    {
        assertTransforms(foreignMembers, json("{'x:user':'" + HUGE + "'}"), json("{}"));
    }

    @Test
    public void shouldRestoreServiceTier()
    {
        assertTransforms(foreignMembers,
            json("{'x:service_tier':'auto'}"),
            json("{'service_tier':'auto'}"));
        assertTransforms(foreignMembers,
            json("{'x:service_tier':'default'}"),
            json("{'service_tier':'default'}"));
        assertTransforms(foreignMembers,
            json("{'x:service_tier':'flex'}"),
            json("{'service_tier':'flex'}"));
        assertTransforms(foreignMembers,
            json("{'x:service_tier':'priority'}"),
            json("{'service_tier':'priority'}"));
    }

    @Test
    public void shouldDropServiceTierThatIsNotCanonical()
    {
        assertTransforms(foreignMembers, json("{'x:service_tier':'scale'}"), json("{}"));
        assertTransforms(foreignMembers, json("{'x:service_tier':'" + HUGE + "'}"), json("{}"));
        assertTransforms(foreignMembers, json("{'x:service_tier':3}"), json("{}"));
        assertTransforms(foreignMembers, json("{'x:service_tier':null}"), json("{}"));
    }

    @Test
    public void shouldDropServiceTierTheModelDoesNotSupport()
    {
        final LlmOpenaiControlFromCanonical restricted = LlmOpenaiControlFromCanonical.members(CCS, false,
            tiers(List.of("auto", "default")));

        assertTransforms(restricted,
            json("{'x:service_tier':'flex'}"),
            json("{}"));
        assertTransforms(restricted,
            json("{'x:service_tier':'auto'}"),
            json("{'service_tier':'auto'}"));
    }

    @Test
    public void shouldDropServiceTierWhenExtracting()
    {
        final LlmOpenaiControlFromCanonical extracting = new LlmOpenaiControlFromCanonical(CCS, false);

        assertTransforms(extracting, json("{'x:service_tier':'auto'}"), "null");
    }

    @Test
    public void shouldRestoreOwnPrefixedServiceTier()
    {
        assertTransforms(transform, json("{'openai:service_tier':'scale'}"), json("'scale'"));
    }

    private static LlmModelCapability tiers(
        List<String> names)
    {
        return new LlmModelCapability(true, null, -1, -1, null, LlmServiceTier.maskOf(names));
    }

    @Test
    public void shouldRejectBehaviouralMemberOfResponses()
    {
        assertRejects(sameMembers, json("{'openai/rs:previous_response_id':'resp_1'}"));

        assertThat(sameMembers.rejectedMember(), equalTo("openai/rs:previous_response_id"));
    }

    @Test
    public void shouldRejectEachBehaviouralMemberOfResponses()
    {
        assertRejects(sameMembers, json("{'openai/rs:background':true}"));
        assertRejects(sameMembers, json("{'openai/rs:conversation':'conv_1'}"));
        assertRejects(sameMembers, json("{'openai/rs:conversation':{'id':'conv_1'}}"));
    }

    @Test
    public void shouldDropHarmlessBehaviouralMemberOfResponses()
    {
        assertTransforms(sameMembers,
            json("{'openai/rs:previous_response_id':null,'openai/rs:background':false,'openai/rs:conversation':null," +
                "'x:seed':1}"),
            json("{'seed':1}"));
    }

    @Test
    public void shouldDropMemberOfResponsesThatIsNotBehavioural()
    {
        assertTransforms(sameMembers,
            json("{'openai/rs:truncation':'auto','openai/rs:include':[],'x:seed':1}"),
            json("{'seed':1}"));
    }

    @Test
    public void shouldNotRejectBehaviouralMemberNamedUnderVendorPrefix()
    {
        assertTransforms(sameMembers, json("{'openai:background':false,'x:seed':1}"), json("{'background':false,'seed':1}"));
    }
}
