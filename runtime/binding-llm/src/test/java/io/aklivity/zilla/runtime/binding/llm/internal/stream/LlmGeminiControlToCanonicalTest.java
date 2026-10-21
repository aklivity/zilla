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
}
