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

public class LlmGeminiControlToCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmGeminiControlToCanonical transform = new LlmGeminiControlToCanonical();

    @Test
    public void shouldPromoteAutoWithoutNames()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'AUTO'}}}"),
            json("{'x:tool_choice':'auto'}"));
    }

    @Test
    public void shouldPromoteAnyWithoutNamesAsRequired()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY'}}}"),
            json("{'x:tool_choice':'required'}"));
    }

    @Test
    public void shouldPromoteNone()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'NONE'}}}"),
            json("{'x:tool_choice':'none'}"));
    }

    @Test
    public void shouldPromoteAnyWithOneNameAsFunction()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'allowedFunctionNames':['get_weather'],'mode':'ANY'}}}"),
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"));
    }

    @Test
    public void shouldPromoteAnyWithSeveralNamesAsAllowedRequired()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':['a','b']}}}"),
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'required'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"));
    }

    @Test
    public void shouldPromoteAutoWithNamesAsAllowedAuto()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'AUTO','allowedFunctionNames':['a','b']}}}"),
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'auto'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"));
    }

    @Test
    public void shouldPromoteAnyWithEmptyNamesAsRequired()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':[]}}}"),
            json("{'x:tool_choice':'required'}"));
    }

    @Test
    public void shouldKeepValidatedAsResidue()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'VALIDATED'}}}"),
            json("{'gemini:toolConfig':{'functionCallingConfig':{'mode':'VALIDATED'}}}"));
    }

    @Test
    public void shouldKeepNoneWithNamesAsResidue()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'NONE','allowedFunctionNames':['a']}}}"),
            json("{'gemini:toolConfig':{'functionCallingConfig':{'mode':'NONE','allowedFunctionNames':['a']}}}"));
    }

    @Test
    public void shouldKeepUnknownCallingConfigMemberAsResidue()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY','extra':1}}}"),
            json("{'gemini:toolConfig':{'functionCallingConfig':{'mode':'ANY','extra':1}}}"));
    }

    @Test
    public void shouldKeepMissingModeAsResidue()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'allowedFunctionNames':['a']}}}"),
            json("{'gemini:toolConfig':{'functionCallingConfig':{'allowedFunctionNames':['a']}}}"));
    }

    @Test
    public void shouldKeepNonStringNameAsResidue()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':[1]}}}"),
            json("{'gemini:toolConfig':{'functionCallingConfig':{'mode':'ANY','allowedFunctionNames':[1]}}}"));
    }

    @Test
    public void shouldSplitResidueAroundLiftedChoice()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'retrievalConfig':{'latLng':{'latitude':1.5}}," +
                "'functionCallingConfig':{'mode':'ANY'},'other':true}}"),
            json("{'x:tool_choice':'required'," +
                "'gemini:toolConfig':{'retrievalConfig':{'latLng':{'latitude':1.5}}," +
                "'other':true}}"));
    }

    @Test
    public void shouldPromoteSnakeCaseAndKeepSourceSpellingOfResidue()
    {
        assertTransforms(transform,
            json("{'tool_config':{'function_calling_config':{'mode':'ANY'," +
                "'allowed_function_names':['get_weather']},'retrieval_config':{'x':1}}}"),
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}," +
                "'gemini:tool_config':{'retrieval_config':{'x':1}}}"));
    }

    @Test
    public void shouldKeepEmptyToolConfig()
    {
        assertTransforms(transform,
            json("{'toolConfig':{}}"),
            json("{'gemini:toolConfig':{}}"));
    }

    @Test
    public void shouldKeepNullToolConfig()
    {
        assertTransforms(transform,
            json("{'toolConfig':null}"),
            json("{'gemini:toolConfig':null}"));
    }

    @Test
    public void shouldKeepToolConfigWithoutCallingConfig()
    {
        assertTransforms(transform,
            json("{'toolConfig':{'retrievalConfig':{}}}"),
            json("{'gemini:toolConfig':{'retrievalConfig':{}}}"));
    }

    @Test
    public void shouldRejectToolConfigLargerThanTheCaptureLimit()
    {
        assertRejects(transform, json("{'toolConfig':{'functionCallingConfig':{'mode':'ANY'," +
            "'allowedFunctionNames':['" + HUGE + "']}}}"));
    }


    @Test
    public void shouldLiftJsonObjectResponseFormat()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'APPLICATION_JSON'}}}"),
            json("{'x:response_format':{'type':'json_object'}}"));
    }

    @Test
    public void shouldLiftJsonSchemaResponseFormat()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'APPLICATION_JSON','schema':{'type':'object'}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{'type':'object'}}}}"));
    }

    @Test
    public void shouldLiftLowercaseMimeType()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'application/json'}}}"),
            json("{'x:response_format':{'type':'json_object'}}"));
    }

    @Test
    public void shouldLiftSnakeCaseResponseFormat()
    {
        assertTransforms(transform,
            json("{'response_format':{'text':{'mime_type':'application/json','schema':{}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{}}}}"));
    }

    @Test
    public void shouldStreamSchemaLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'APPLICATION_JSON','schema':{'d':'" + HUGE + "'}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{'d':'" + HUGE +
                "'}}}}"));
    }

    @Test
    public void shouldKeepOtherMimeTypeAsResidue()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'text/x.enum','schema':{'enum':['a']}}}}"),
            json("{'gemini:generationConfig':{'responseFormat':{'text':{'mimeType':'text/x.enum','schema':{'enum':['a']}}}}}"));
    }

    @Test
    public void shouldKeepSchemaBeforeMimeTypeAsResidue()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'schema':{'a':1},'mimeType':'APPLICATION_JSON'}}}"),
            json("{'gemini:generationConfig':{'responseFormat':{'text':{'schema':{'a':1},'mimeType':'APPLICATION_JSON'}}}}"));
    }

    @Test
    public void shouldKeepUnknownFormatMemberAsResidue()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'other':[1]}}"),
            json("{'gemini:generationConfig':{'responseFormat':{'other':[1]}}}"));
    }

    @Test
    public void shouldKeepEmptyResponseFormatAsResidue()
    {
        assertTransforms(transform,
            json("{'responseFormat':{}}"),
            json("{'gemini:generationConfig':{'responseFormat':{}}}"));
        assertTransforms(transform,
            json("{'responseFormat':{'text':{}}}"),
            json("{'gemini:generationConfig':{'responseFormat':{'text':{}}}}"));
    }

    @Test
    public void shouldKeepResponseFormatThatIsNotAnObjectAsResidue()
    {
        assertTransforms(transform,
            json("{'responseFormat':[{'text':{}}]}"),
            json("{'gemini:generationConfig':{'responseFormat':[{'text':{}}]}}"));
    }

    @Test
    public void shouldKeepResidueInConfiguredKeySpelling()
    {
        transform.residueKey("gemini:generation_config");

        assertTransforms(transform,
            json("{'responseFormat':{'other':1}}"),
            json("{'gemini:generation_config':{'responseFormat':{'other':1}}}"));
    }

    @Test
    public void shouldDropOtherMembersAfterLiftedText()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'APPLICATION_JSON'},'other':1}}"),
            json("{'x:response_format':{'type':'json_object'}}"));
    }

    @Test
    public void shouldDropOtherTextMembersWhenLifted()
    {
        assertTransforms(transform,
            json("{'responseFormat':{'text':{'mimeType':'APPLICATION_JSON','other':1,'schema':{}}}}"),
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'response','schema':{}}}}"));
    }

    @Test
    public void shouldLiftThinkingLevel()
    {
        for (String level : new String[] {"MINIMAL", "LOW", "MEDIUM", "HIGH"})
        {
            assertTransforms(transform,
                json("{'thinkingConfig':{'thinkingLevel':'" + level + "'}}"),
                json("{'x:reasoning_effort':'" + level.toLowerCase() + "'}"));
        }
    }

    @Test
    public void shouldLiftLowercaseThinkingLevel()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingLevel':'medium'}}"),
            json("{'x:reasoning_effort':'medium'}"));
    }

    @Test
    public void shouldLiftSnakeCaseThinkingLevel()
    {
        assertTransforms(transform,
            json("{'thinking_config':{'thinking_level':'LOW'}}"),
            json("{'x:reasoning_effort':'low'}"));
    }

    @Test
    public void shouldLiftZeroThinkingBudgetAsNone()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingBudget':0}}"),
            json("{'x:reasoning_effort':'none'}"));
    }

    @Test
    public void shouldLiftThinkingBudget()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingBudget':4096}}"),
            json("{'x:thinking_budget_tokens':4096}"));
    }

    @Test
    public void shouldLiftSnakeCaseThinkingBudget()
    {
        assertTransforms(transform,
            json("{'thinking_config':{'thinking_budget':4096}}"),
            json("{'x:thinking_budget_tokens':4096}"));
    }

    @Test
    public void shouldKeepAutomaticThinkingBudgetAsResidue()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingBudget':-1}}"),
            json("{'gemini:thinkingConfig':{'thinkingBudget':-1}}"));
    }

    @Test
    public void shouldKeepFractionalThinkingBudgetAsResidue()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingBudget':1.5}}"),
            json("{'gemini:thinkingConfig':{'thinkingBudget':1.5}}"));
    }

    @Test
    public void shouldKeepUnknownThinkingLevelAsResidue()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingLevel':'THINKING_LEVEL_UNSPECIFIED'}}"),
            json("{'gemini:thinkingConfig':{'thinkingLevel':'THINKING_LEVEL_UNSPECIFIED'}}"));
    }

    @Test
    public void shouldKeepThinkingLevelThatIsNotAStringAsResidue()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingLevel':3}}"),
            json("{'gemini:thinkingConfig':{'thinkingLevel':3}}"));
    }

    @Test
    public void shouldKeepOtherThinkingMembersAsResidueAfterLevel()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'thinkingLevel':'HIGH','includeThoughts':true}}"),
            json("{'x:reasoning_effort':'high','gemini:thinkingConfig':{'includeThoughts':true}}"));
    }

    @Test
    public void shouldKeepOtherThinkingMembersAsResidueBeforeLevel()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'includeThoughts':true,'thinkingLevel':'HIGH'}}"),
            json("{'gemini:thinkingConfig':{'includeThoughts':true},'x:reasoning_effort':'high'}"));
    }

    @Test
    public void shouldSplitThinkingResidueAroundLiftedMembers()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'a':1,'thinkingLevel':'LOW','b':2,'thinkingBudget':-1}}"),
            json("{'gemini:thinkingConfig':{'a':1},'x:reasoning_effort':'low','gemini:thinkingConfig':{'b':2," +
                "'thinkingBudget':-1}}"));
    }

    @Test
    public void shouldKeepResidueOnlyThinkingConfig()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{'includeThoughts':true}}"),
            json("{'gemini:thinkingConfig':{'includeThoughts':true}}"));
    }

    @Test
    public void shouldKeepEmptyThinkingConfigAsResidue()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':{}}"),
            json("{'gemini:thinkingConfig':{}}"));
    }

    @Test
    public void shouldKeepEmptySnakeCaseThinkingConfigInSourceSpelling()
    {
        assertTransforms(transform,
            json("{'thinking_config':{}}"),
            json("{'gemini:thinking_config':{}}"));
    }

    @Test
    public void shouldKeepNullThinkingConfigAsResidue()
    {
        assertTransforms(transform,
            json("{'thinkingConfig':null}"),
            json("{'gemini:thinkingConfig':null}"));
    }

    @Test
    public void shouldPromoteServiceTier()
    {
        assertTransforms(transform,
            json("{'serviceTier':'standard'}"),
            json("{'x:service_tier':'default'}"));
        assertTransforms(transform,
            json("{'serviceTier':'STANDARD'}"),
            json("{'x:service_tier':'default'}"));
        assertTransforms(transform,
            json("{'serviceTier':'flex'}"),
            json("{'x:service_tier':'flex'}"));
        assertTransforms(transform,
            json("{'serviceTier':'FLEX'}"),
            json("{'x:service_tier':'flex'}"));
        assertTransforms(transform,
            json("{'serviceTier':'priority'}"),
            json("{'x:service_tier':'priority'}"));
        assertTransforms(transform,
            json("{'serviceTier':'Priority'}"),
            json("{'x:service_tier':'priority'}"));
    }

    @Test
    public void shouldKeepServiceTierOfOtherValuesPrefixed()
    {
        assertTransforms(transform,
            json("{'serviceTier':'unspecified'}"),
            json("{'gemini:serviceTier':'unspecified'}"));
        assertTransforms(transform,
            json("{'serviceTier':'TIER_X'}"),
            json("{'gemini:serviceTier':'TIER_X'}"));
        assertTransforms(transform,
            json("{'serviceTier':7}"),
            json("{'gemini:serviceTier':7}"));
        assertTransforms(transform,
            json("{'serviceTier':null}"),
            json("{'gemini:serviceTier':null}"));
    }

    @Test
    public void shouldPromoteServiceTierWithEscapes()
    {
        assertTransforms(transform,
            "{\"serviceTier\":\"st\\u0061ndard\"}",
            json("{'x:service_tier':'default'}"));
    }

    @Test
    public void shouldKeepSnakeServiceTierPrefixedInSourceSpelling()
    {
        assertTransforms(transform,
            json("{'service_tier':'standard'}"),
            json("{'x:service_tier':'default'}"));
        assertTransforms(transform,
            json("{'service_tier':'TIER_X'}"),
            json("{'gemini:service_tier':'TIER_X'}"));
    }
}
