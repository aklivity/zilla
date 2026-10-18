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

public class LlmAnthropicReasoningToCanonicalTest
{
    private static final String TEXT = "Let me think. ".repeat(400);

    private final LlmAnthropicReasoningToCanonical reasoning = new LlmAnthropicReasoningToCanonical();

    @Test
    public void shouldConvertThinkingBlock()
    {
        assertTransforms(reasoning,
            json("{'type':'thinking','thinking':'Let me think.','signature':'sig-1'}"),
            json("{'text':'Let me think.','anthropic:signature':'sig-1'}"));
    }

    @Test
    public void shouldConvertThinkingBlockWithSignatureFirst()
    {
        assertTransforms(reasoning,
            json("{'type':'thinking','signature':'sig-1','thinking':'Let me think.'}"),
            json("{'anthropic:signature':'sig-1','text':'Let me think.'}"));
    }

    @Test
    public void shouldConvertThinkingBlockLargerThanWindow()
    {
        assertTransforms(reasoning,
            json("{'type':'thinking','thinking':'" + TEXT + "','signature':'sig-1'}"),
            json("{'text':'" + TEXT + "','anthropic:signature':'sig-1'}"));
    }

    @Test
    public void shouldConvertEscapedThinking()
    {
        assertTransforms(reasoning,
            json("{'type':'thinking','thinking':'line one\\nline \\\"two\\\"'}"),
            json("{'text':'line one\\nline \\\"two\\\"'}"));
    }

    @Test
    public void shouldConvertRedactedBlock()
    {
        assertTransforms(reasoning,
            json("{'type':'redacted_thinking','data':'opaque'}"),
            json("{'anthropic:data':'opaque'}"));
    }

    @Test
    public void shouldPrefixNativeOnlyMembersAtTheirPeers()
    {
        assertTransforms(reasoning,
            json("{'cache_control':{'type':'ephemeral'},'type':'thinking','thinking':'t'}"),
            json("{'anthropic:cache_control':{'type':'ephemeral'},'text':'t'}"));
    }

    @Test
    public void shouldRejectThinkingThatIsNotAString()
    {
        assertRejects(reasoning, json("{'type':'thinking','thinking':{'a':1}}"));
    }

    @Test
    public void shouldRejectBlockThatIsNotAnObject()
    {
        assertRejects(reasoning, json("['thinking']"));
    }
}
