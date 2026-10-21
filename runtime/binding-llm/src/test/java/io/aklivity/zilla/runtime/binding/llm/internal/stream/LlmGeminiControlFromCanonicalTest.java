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

public class LlmGeminiControlFromCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmGeminiControlFromCanonical transform = new LlmGeminiControlFromCanonical(true);
    private final LlmGeminiControlFromCanonical sameMembers = LlmGeminiControlFromCanonical.members(true);
    private final LlmGeminiControlFromCanonical foreignMembers = LlmGeminiControlFromCanonical.members(false);

    @Test
    public void shouldRestoreOwnPrefixedMember()
    {
        assertTransforms(transform, json("{'gemini:cachedContent':'cachedContents/abc'}"), json("'cachedContents/abc'"));

        assertThat(transform.member(), equalTo("cachedContent"));
    }

    @Test
    public void shouldRestoreObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'gemini:generationConfig':{'temperature':0.2,'stopSequences':['a','" + LONG + "'],'openai:c':true}}"),
            json("{'temperature':0.2,'stopSequences':['a','" + LONG + "'],'openai:c':true}"));

        assertThat(transform.member(), equalTo("generationConfig"));
    }

    @Test
    public void shouldRestoreValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'gemini:labels':{'note':'" + HUGE + "'}}"),
            json("{'note':'" + HUGE + "'}"));

        assertThat(transform.member(), equalTo("labels"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMember()
    {
        assertTransforms(transform, json("{'openai:logit_bias':{'a':1}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldDropStream()
    {
        assertTransforms(transform, json("{'stream':true}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldDropStreamOptions()
    {
        assertTransforms(transform, json("{'stream_options':{'include_usage':true}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldRestoreSharedMembersAsNativeContainerFragments()
    {
        assertTransforms(foreignMembers,
            json("{'x:temperature':0.7,'x:top_p':0.9,'x:top_k':40,'x:frequency_penalty':0.5,'x:presence_penalty':-0.5}"),
            json("{'generationConfig':{'temperature':0.7},'generationConfig':{'topP':0.9},'generationConfig':{'topK':40}," +
                "'generationConfig':{'frequencyPenalty':0.5},'generationConfig':{'presencePenalty':-0.5}}"));
    }

    @Test
    public void shouldRestoreStopAsStopSequences()
    {
        assertTransforms(foreignMembers,
            json("{'x:stop':['a','" + LONG + "']}"),
            json("{'generationConfig':{'stopSequences':['a','" + LONG + "']}}"));
    }

    @Test
    public void shouldRestoreEmptyStop()
    {
        assertTransforms(foreignMembers, json("{'x:stop':[]}"), json("{'generationConfig':{'stopSequences':[]}}"));
    }

    @Test
    public void shouldAcceptStopUpToTheLimit()
    {
        assertTransforms(foreignMembers,
            json("{'x:stop':['a','b','c','d','e']}"),
            json("{'generationConfig':{'stopSequences':['a','b','c','d','e']}}"));
    }

    @Test
    public void shouldRejectStopBeyondTheLimit()
    {
        assertRejects(foreignMembers, json("{'x:stop':['a','b','c','d','e','f']}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("stop"));
    }

    @Test
    public void shouldRestoreStopBeyondTheLimitWhenSameDialect()
    {
        assertTransforms(sameMembers,
            json("{'x:stop':['a','b','c','d','e','f']}"),
            json("{'generationConfig':{'stopSequences':['a','b','c','d','e','f']}}"));
    }

    @Test
    public void shouldDropNullStop()
    {
        assertTransforms(foreignMembers, json("{'x:stop':null,'x:top_k':1}"), json("{'generationConfig':{'topK':1}}"));
    }

    @Test
    public void shouldRejectStopThatIsNotAnArray()
    {
        assertRejects(foreignMembers, json("{'x:stop':'a'}"));
    }

    @Test
    public void shouldRestoreSeedWithinInt32()
    {
        assertTransforms(foreignMembers, json("{'x:seed':42}"), json("{'generationConfig':{'seed':42}}"));
        assertTransforms(foreignMembers, json("{'x:seed':2147483647}"), json("{'generationConfig':{'seed':2147483647}}"));
        assertTransforms(foreignMembers, json("{'x:seed':-2147483648}"), json("{'generationConfig':{'seed':-2147483648}}"));
    }

    @Test
    public void shouldDropSeedOutsideInt32()
    {
        assertTransforms(foreignMembers, json("{'x:seed':2147483648,'x:top_k':1}"), json("{'generationConfig':{'topK':1}}"));
        assertTransforms(foreignMembers, json("{'x:seed':-2147483649}"), json("{}"));
        assertTransforms(foreignMembers, json("{'x:seed':99999999999999999999}"), json("{}"));
    }

    @Test
    public void shouldDropSeedThatIsNotIntegral()
    {
        assertTransforms(foreignMembers, json("{'x:seed':1.5}"), json("{}"));
    }

    @Test
    public void shouldDropNullSeed()
    {
        assertTransforms(foreignMembers, json("{'x:seed':null}"), json("{}"));
    }

    @Test
    public void shouldRejectSeedThatIsNotANumber()
    {
        assertRejects(foreignMembers, json("{'x:seed':'a'}"));
    }

    @Test
    public void shouldRestoreSharedMemberAsOwnFragmentBetweenOtherMembers()
    {
        assertTransforms(sameMembers,
            json("{'x:temperature':0.7,'gemini:safetySettings':[{'a':1}],'x:top_p':0.9," +
                "'gemini:generationConfig':{'candidateCount':1}}"),
            json("{'generationConfig':{'temperature':0.7},'safetySettings':[{'a':1}],'generationConfig':{'topP':0.9}," +
                "'generationConfig':{'candidateCount':1}}"));
    }

    @Test
    public void shouldRestoreResidueFragmentsOfEitherSpelling()
    {
        assertTransforms(sameMembers,
            json("{'gemini:generationConfig':{'candidateCount':1},'gemini:generation_config':{'candidate_count':2}}"),
            json("{'generationConfig':{'candidateCount':1},'generationConfig':{'candidate_count':2}}"));
    }

    @Test
    public void shouldDropResidueAcrossDialects()
    {
        assertTransforms(foreignMembers,
            json("{'gemini:generationConfig':{'candidateCount':1},'x:temperature':0.7}"),
            json("{'generationConfig':{'temperature':0.7}}"));
    }

    @Test
    public void shouldDropSharedMembersTheDialectDoesNotKnow()
    {
        assertTransforms(foreignMembers,
            json("{'x:unknown':1,'x:temperature':0.7}"),
            json("{'generationConfig':{'temperature':0.7}}"));
    }

    @Test
    public void shouldPassValueLargerThanWindowInContainerFragment()
    {
        assertTransforms(foreignMembers,
            json("{'x:temperature':0.7,'x:stop':['" + HUGE + "']}"),
            json("{'generationConfig':{'temperature':0.7},'generationConfig':{'stopSequences':['" + HUGE + "']}}"));
    }

    @Test
    public void shouldRestoreToolChoiceAuto()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'auto'}"),
            json("{'toolConfig':{'functionCallingConfig':{'mode':'AUTO'}}}"));
    }

    @Test
    public void shouldRestoreToolChoiceRequiredAsAny()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'required'}"),
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY'}}}"));
    }

    @Test
    public void shouldRestoreToolChoiceNone()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'none'}"),
            json("{'toolConfig':{'functionCallingConfig':{'mode':'NONE'}}}"));
    }

    @Test
    public void shouldRestoreFunctionToolChoiceAsAnyWithName()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"),
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':['get_weather']}}}"));
    }

    @Test
    public void shouldRestoreAllowedAutoWithNames()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'auto'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"),
            json("{'toolConfig':{'functionCallingConfig':{'mode':'AUTO','allowedFunctionNames':['a','b']}}}"));
    }

    @Test
    public void shouldRestoreAllowedRequiredAsAnyWithNames()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'required'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"),
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':['a','b']}}}"));
    }

    @Test
    public void shouldRejectUnrecognizedToolChoice()
    {
        assertRejects(foreignMembers, json("{'x:tool_choice':'bogus'}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("tool_choice"));
    }

    @Test
    public void shouldDropParallelToolCallsEnabled()
    {
        assertTransforms(foreignMembers,
            json("{'x:parallel_tool_calls':true}"),
            json("{}"));
    }

    @Test
    public void shouldRejectParallelToolCallsDisabled()
    {
        assertRejects(foreignMembers, json("{'x:parallel_tool_calls':false}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("parallel_tool_calls"));
    }

    @Test
    public void shouldRestoreResidueAndNormalizeSnakeCase()
    {
        assertTransforms(sameMembers,
            json("{'gemini:tool_config':{'retrieval_config':{'k':1}}}"),
            json("{'toolConfig':{'retrieval_config':{'k':1}}}"));
    }
}
