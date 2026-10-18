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

import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmAnthropicReasoningToDeltaTest
{
    private static final String TEXT = "Let me think. ".repeat(400);

    private final LlmAnthropicReasoningToDelta delta = new LlmAnthropicReasoningToDelta();

    @Test
    public void shouldConvertTextToThinkingDelta()
    {
        delta.index(2);

        assertTransforms(delta,
            json("{'text':'Let me think.'}"),
            json("{'type':'content_block_delta','index':2,'delta':{'type':'thinking_delta','thinking':'Let me think.'}}"));
    }

    @Test
    public void shouldConvertSignatureToSignatureDelta()
    {
        assertTransforms(delta,
            json("{'anthropic:signature':'sig-1'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'signature_delta','signature':'sig-1'}}"));
    }

    @Test
    public void shouldConvertTextLargerThanWindow()
    {
        assertTransforms(delta,
            json("{'text':'" + TEXT + "'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'thinking_delta','thinking':'" + TEXT + "'}}"));
    }

    @Test
    public void shouldConvertEscapedText()
    {
        assertTransforms(delta,
            json("{'text':'line one\\nline \\\"two\\\"'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'thinking_delta'," +
                "'thinking':'line one\\nline \\\"two\\\"'}}"));
    }

    @Test
    public void shouldDropOtherMembers()
    {
        assertTransforms(delta,
            json("{'openai:note':{'a':[1,2]},'text':'t','anthropic:cache_control':'x'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'thinking_delta','thinking':'t'}}"));
    }

    @Test
    public void shouldKeepOnlyFirstConvertibleMember()
    {
        assertTransforms(delta,
            json("{'text':'t','anthropic:signature':'sig-1'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'thinking_delta','thinking':'t'}}"));
    }

    @Test
    public void shouldProduceNothingWithoutConvertibleMember()
    {
        assertTransforms(delta, json("{'anthropic:data':'opaque'}"), "");
    }
}
