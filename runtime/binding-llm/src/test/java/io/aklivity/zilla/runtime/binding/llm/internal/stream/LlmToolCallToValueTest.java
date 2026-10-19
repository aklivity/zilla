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

public class LlmToolCallToValueTest
{
    private static final String HUGE = "x".repeat(5000);

    private final LlmToolCallToValue transform = new LlmToolCallToValue();

    @Test
    public void shouldExtractArguments()
    {
        assertTransforms(transform,
            json("{'arguments':{'city':'Paris'}}"),
            json("{'city':'Paris'}"));
    }

    @Test
    public void shouldExtractNestedArguments()
    {
        assertTransforms(transform,
            json("{'arguments':{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}}"),
            json("{'n':[1,2.5,true,null,{'k':'v'}],'e':{}}"));
    }

    @Test
    public void shouldExtractValueLargerThanWindow()
    {
        assertTransforms(transform,
            json("{'arguments':{'text':'" + HUGE + "'}}"),
            json("{'text':'" + HUGE + "'}"));
    }

    @Test
    public void shouldExtractArgumentsAmongNativeOnlyMembers()
    {
        assertTransforms(transform,
            json("{'anthropic:cache_control':{'type':'ephemeral'},'arguments':{'a':1},'openai:index':0}"),
            json("{'a':1}"));
    }

    @Test
    public void shouldRejectArgumentsThatAreNotAnObject()
    {
        assertRejects(transform, json("{'arguments':[1]}"));
        assertRejects(transform, json("{'arguments':'x'}"));
    }
}
