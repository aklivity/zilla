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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransformsCompletedBeforeLate;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import java.util.function.Consumer;

import org.junit.Test;

public class LlmAnthropicToolCallFromCanonicalTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmAnthropicToolCallFromCanonical transform = new LlmAnthropicToolCallFromCanonical("tool_use");

    private final Consumer<LlmSectionAttributes> id = attributes -> attributes.toolCallId("toolu_1");
    private final Consumer<LlmSectionAttributes> name = attributes -> attributes.toolName("get_weather");
    private final Consumer<LlmSectionAttributes> both = id.andThen(name);

    @Test
    public void shouldWriteInputWithAttributesKnownAtBegin()
    {
        assertTransforms(transform, both, null,
            json("{'arguments':{'city':'Paris'}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'city':'Paris'}}"));
    }

    @Test
    public void shouldWriteInputAsServerToolUse()
    {
        assertTransforms(new LlmAnthropicToolCallFromCanonical("server_tool_use"), both, null,
            json("{'arguments':{'query':'zilla'}}"),
            json("{'type':'server_tool_use','id':'toolu_1','name':'get_weather','input':{'query':'zilla'}}"));
    }

    @Test
    public void shouldWriteNestedInput()
    {
        assertTransforms(transform, both, null,
            json("{'arguments':{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}}"));
    }

    @Test
    public void shouldWriteValueLargerThanWindow()
    {
        assertTransforms(transform, both, null,
            json("{'arguments':{'text':'" + HUGE + "'}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'text':'" + HUGE + "'}}"));
    }

    @Test
    public void shouldWriteEmptyInputWhenArgumentsAbsent()
    {
        assertTransforms(transform, both, null,
            json("{}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{}}"));
    }

    @Test
    public void shouldWriteNameAfterInputWhenKnownLate()
    {
        assertTransforms(transform, id, name,
            json("{'arguments':{'text':'" + HUGE + "'}}"),
            json("{'type':'tool_use','id':'toolu_1','input':{'text':'" + HUGE + "'},'name':'get_weather'}"));
    }

    @Test
    public void shouldWriteIdAfterInputWhenKnownLate()
    {
        assertTransforms(transform, name, id,
            json("{'arguments':{'text':'" + HUGE + "'}}"),
            json("{'type':'tool_use','name':'get_weather','input':{'text':'" + HUGE + "'},'id':'toolu_1'}"));
    }

    @Test
    public void shouldWriteIdAndNameAfterInputWhenKnownLate()
    {
        assertTransforms(transform, null, both,
            json("{'arguments':{'text':'" + HUGE + "'}}"),
            json("{'type':'tool_use','input':{'text':'" + HUGE + "'},'id':'toolu_1','name':'get_weather'}"));
    }

    @Test
    public void shouldWriteIdAndNameKnownLateAfterTheObjectCompleted()
    {
        assertTransformsCompletedBeforeLate(transform, null, both,
            json("{'arguments':{'city':'Paris'}}"),
            json("{'type':'tool_use','input':{'city':'Paris'},'id':'toolu_1','name':'get_weather'}"));
    }

    @Test
    public void shouldRestoreOwnMembersBeforeAndAfterInput()
    {
        assertTransforms(transform, both, null,
            json("{'anthropic:cache_control':{'type':'ephemeral'},'arguments':{'a':1},'anthropic:note':'" + HUGE + "'}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','cache_control':{'type':'ephemeral'}," +
                "'input':{'a':1},'note':'" + HUGE + "'}"));
    }

    @Test
    public void shouldDropOtherDialectMembersAndKeepUnprefixedMembers()
    {
        assertTransforms(transform, both, null,
            json("{'openai:index':0,'arguments':{'a':1},'kept':true,'openai:top':{'b':[1]}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'a':1},'kept':true}"));
    }

    @Test
    public void shouldRejectMissingId()
    {
        assertRejects(transform, name, null, json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, id, null, json("{'arguments':{'city':'Paris'}}"));
    }

    @Test
    public void shouldRejectArgumentsThatAreNotAnObject()
    {
        assertRejects(transform, both, null, json("{'arguments':[1]}"));
        assertRejects(transform, both, null, json("{'arguments':5}"));
        assertRejects(transform, both, null, json("{'arguments':'x'}"));
    }

    @Test
    public void shouldRejectToolCallThatIsNotAnObject()
    {
        assertRejects(transform, both, null, json("[1]"));
        assertRejects(transform, both, null, json("5"));
        assertRejects(transform, both, null, " ");
    }
}
