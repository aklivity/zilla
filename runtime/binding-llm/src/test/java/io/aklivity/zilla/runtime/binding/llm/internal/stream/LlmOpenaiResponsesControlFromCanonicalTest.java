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

public class LlmOpenaiResponsesControlFromCanonicalTest
{
    private static final String LONG = "x".repeat(300);
    private static final String HUGE = "x".repeat(5000);

    private final LlmOpenaiResponsesControlFromCanonical transform = new LlmOpenaiResponsesControlFromCanonical(true);
    private final LlmOpenaiResponsesControlFromCanonical foreign = new LlmOpenaiResponsesControlFromCanonical(false);
    private final LlmOpenaiResponsesControlFromCanonical foreignMembers = LlmOpenaiResponsesControlFromCanonical.members(false);

    @Test
    public void shouldRestoreOwnPrefixedMember()
    {
        assertTransforms(transform, json("{'openai:store':false}"), "false");

        assertThat(transform.member(), equalTo("store"));
    }

    @Test
    public void shouldRestoreObjectValueUnchanged()
    {
        assertTransforms(transform,
            json("{'openai:text':{'format':{'type':'json_schema','schema':{'a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}}}"),
            json("{'format':{'type':'json_schema','schema':{'a':[1,{'b':'" + LONG + "'}],'anthropic:c':true}}}"));

        assertThat(transform.member(), equalTo("text"));
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
    public void shouldDropOwnPrefixedMemberAcrossDialects()
    {
        assertTransforms(foreign, json("{'openai:store':false}"), "null");

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
        assertTransforms(transform, json("{'anthropic:top_k':5}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldPassStream()
    {
        assertTransforms(transform, json("{'stream':true}"), "true");

        assertThat(transform.member(), equalTo("stream"));
    }

    @Test
    public void shouldDropStreamOptionsWithOnlyUsage()
    {
        assertTransforms(transform, json("{'stream_options':{'include_usage':true}}"), "null");

        assertThat(transform.member(), nullValue());
    }

    @Test
    public void shouldRestoreNativeOptionMembersAndDropUsage()
    {
        assertTransforms(transform,
            json("{'stream_options':{'include_usage':true,'openai:include_obfuscation':false}}"),
            json("{'include_obfuscation':false}"));

        assertThat(transform.member(), equalTo("stream_options"));
    }

    @Test
    public void shouldRestoreNativeOptionMembersAndDropForeign()
    {
        assertTransforms(transform,
            json("{'stream_options':{'openai:include_obfuscation':false,'anthropic:other':{'a':[1,'" + LONG +
                "']},'openai:extra':{'a':[1]},'include_usage':true}}"),
            json("{'include_obfuscation':false,'extra':{'a':[1]}}"));

        assertThat(transform.member(), equalTo("stream_options"));
    }

    @Test
    public void shouldDropForeignOptionMembersAcrossDialects()
    {
        assertTransforms(foreign,
            json("{'stream_options':{'include_usage':true,'openai:include_obfuscation':false}}"),
            "null");

        assertThat(foreign.member(), nullValue());
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
            json("{'x:temperature':0.7,'x:top_p':0.9}"),
            json("{'temperature':0.7,'top_p':0.9}"));
    }

    @Test
    public void shouldDropSharedMemberNotSupported()
    {
        assertTransforms(foreignMembers,
            json("{'x:top_k':1,'x:seed':1,'x:frequency_penalty':0.5,'x:presence_penalty':0.5,'x:top_p':0.9}"),
            json("{'top_p':0.9}"));
    }

    @Test
    public void shouldRejectSharedStop()
    {
        assertRejects(foreignMembers, json("{'x:temperature':0.7,'x:stop':['END']}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("stop"));
    }

    @Test
    public void shouldDropSharedMemberWhenExtracting()
    {
        assertTransforms(foreign, json("{'x:temperature':0.7}"), "null");

        assertThat(foreign.member(), nullValue());
    }

    @Test
    public void shouldRestoreToolChoiceAuto()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'auto'}"),
            json("{'tool_choice':'auto'}"));
    }

    @Test
    public void shouldRestoreToolChoiceRequired()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'required'}"),
            json("{'tool_choice':'required'}"));
    }

    @Test
    public void shouldRestoreToolChoiceNone()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':'none'}"),
            json("{'tool_choice':'none'}"));
    }

    @Test
    public void shouldFlattenFunctionToolChoice()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'function','function':{'name':'get_weather'}}}"),
            json("{'tool_choice':{'type':'function','name':'get_weather'}}"));
    }

    @Test
    public void shouldFlattenAllowedToolsToolChoice()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'type':'allowed_tools','allowed_tools':{'mode':'required'," +
                "'tools':[{'type':'function','function':{'name':'a'}},{'type':'function'," +
                "'function':{'name':'b'}}]}}}"),
            json("{'tool_choice':{'type':'allowed_tools','mode':'required'," +
                "'tools':[{'type':'function','name':'a'},{'type':'function','name':'b'}]}}"));
    }

    @Test
    public void shouldFlattenToolChoiceWhenMembersArriveInAnyOrder()
    {
        assertTransforms(foreignMembers,
            json("{'x:tool_choice':{'function':{'name':'f'},'type':'function'}}"),
            json("{'tool_choice':{'type':'function','name':'f'}}"));
    }

    @Test
    public void shouldRestoreParallelToolCallsUnchanged()
    {
        assertTransforms(foreignMembers,
            json("{'x:parallel_tool_calls':false,'x:tool_choice':'auto'}"),
            json("{'parallel_tool_calls':false,'tool_choice':'auto'}"));
    }

    @Test
    public void shouldRejectUnrecognizedToolChoice()
    {
        assertRejects(foreignMembers, json("{'x:tool_choice':'bogus'}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("tool_choice"));
    }

    @Test
    public void shouldRejectToolChoiceWithExtraMember()
    {
        assertRejects(foreignMembers, json("{'x:tool_choice':{'type':'function','function':{'name':'f','strict':true}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("tool_choice"));
    }

    @Test
    public void shouldRejectCustomToolChoice()
    {
        assertRejects(foreignMembers, json("{'x:tool_choice':{'type':'custom','custom':{'name':'f'}}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("tool_choice"));
    }


    @Test
    public void shouldFlattenJsonSchemaFormat()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'answer','description':'d'," +
                "'schema':{'a':[1,{'b':'" + LONG + "'}]},'strict':true}}}"),
            json("{'text':{'format':{'type':'json_schema','name':'answer','description':'d'," +
                "'schema':{'a':[1,{'b':'" + LONG + "'}]},'strict':true}}}"));

        assertThat(foreignMembers.member(), equalTo("text"));
    }

    @Test
    public void shouldFlattenJsonSchemaFormatWithTypeLast()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'json_schema':{'name':'answer','schema':{'a':1}},'type':'json_schema'}}"),
            json("{'text':{'format':{'type':'json_schema','name':'answer','schema':{'a':1}}}}"));
    }

    @Test
    public void shouldFlattenJsonSchemaFormatLargerThanWindow()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'a','schema':{'d':'" + HUGE + "'}}}}"),
            json("{'text':{'format':{'type':'json_schema','name':'a','schema':{'d':'" + HUGE + "'}}}}"));
    }

    @Test
    public void shouldWriteTextFormat()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'text'}}"),
            json("{'text':{'format':{'type':'text'}}}"));
    }

    @Test
    public void shouldWriteJsonObjectFormat()
    {
        assertTransforms(foreignMembers,
            json("{'x:response_format':{'type':'json_object'}}"),
            json("{'text':{'format':{'type':'json_object'}}}"));
    }

    @Test
    public void shouldDropNullFormat()
    {
        assertTransforms(foreignMembers, json("{'x:response_format':null}"), json("{}"));
    }

    @Test
    public void shouldRejectUnknownFormatType()
    {
        assertRejects(foreignMembers, json("{'x:response_format':{'type':'grammar'}}"));

        assertThat(foreignMembers.rejectedMember(), equalTo("response_format"));
    }

    @Test
    public void shouldRejectFormatThatIsNotAnObject()
    {
        assertRejects(foreignMembers, json("{'x:response_format':'text'}"));
    }

    @Test
    public void shouldRejectFormatWithoutType()
    {
        assertRejects(foreignMembers, json("{'x:response_format':{}}"));
    }

    @Test
    public void shouldRejectInconsistentFormat()
    {
        assertRejects(foreignMembers, json("{'x:response_format':{'type':'json_object','json_schema':{'name':'a'}}}"));
        assertRejects(foreignMembers, json("{'x:response_format':{'json_schema':{'name':'a'},'type':'text'}}"));
    }
}
