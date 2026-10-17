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
    public void shouldWrapInputWithAttributesKnownAtBegin()
    {
        assertTransforms(transform, both, null,
            json("{'city':'Paris'}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'city':'Paris'}}"));
    }

    @Test
    public void shouldWrapInputAsServerToolUse()
    {
        assertTransforms(new LlmAnthropicToolCallFromCanonical("server_tool_use"), both, null,
            json("{'query':'zilla'}"),
            json("{'type':'server_tool_use','id':'toolu_1','name':'get_weather','input':{'query':'zilla'}}"));
    }

    @Test
    public void shouldWrapNestedInput()
    {
        assertTransforms(transform, both, null,
            json("{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}}"));
    }

    @Test
    public void shouldWrapValueLargerThanWindow()
    {
        assertTransforms(transform, both, null,
            json("{'text':'" + HUGE + "'}"),
            json("{'type':'tool_use','id':'toolu_1','name':'get_weather','input':{'text':'" + HUGE + "'}}"));
    }

    @Test
    public void shouldWriteNameAfterInputWhenKnownLate()
    {
        assertTransforms(transform, id, name,
            json("{'text':'" + HUGE + "'}"),
            json("{'type':'tool_use','id':'toolu_1','input':{'text':'" + HUGE + "'},'name':'get_weather'}"));
    }

    @Test
    public void shouldWriteIdAfterInputWhenKnownLate()
    {
        assertTransforms(transform, name, id,
            json("{'text':'" + HUGE + "'}"),
            json("{'type':'tool_use','name':'get_weather','input':{'text':'" + HUGE + "'},'id':'toolu_1'}"));
    }

    @Test
    public void shouldWriteIdAndNameAfterInputWhenKnownLate()
    {
        assertTransforms(transform, null, both,
            json("{'text':'" + HUGE + "'}"),
            json("{'type':'tool_use','input':{'text':'" + HUGE + "'},'id':'toolu_1','name':'get_weather'}"));
    }

    @Test
    public void shouldRejectMissingId()
    {
        assertRejects(transform, name, null, json("{'city':'Paris'}"));
    }

    @Test
    public void shouldRejectMissingName()
    {
        assertRejects(transform, id, null, json("{'city':'Paris'}"));
    }

    @Test
    public void shouldRejectInputThatIsNotAnObject()
    {
        assertRejects(transform, both, null, json("[1]"));
        assertRejects(transform, both, null, json("5"));
        assertRejects(transform, both, null, json("'x'"));
    }

    @Test
    public void shouldRejectMissingInput()
    {
        assertRejects(transform, both, null, " ");
    }
}
