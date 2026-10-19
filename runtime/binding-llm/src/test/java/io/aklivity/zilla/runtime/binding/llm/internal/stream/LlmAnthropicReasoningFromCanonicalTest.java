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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRoundTrips;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;

import org.junit.Test;

public class LlmAnthropicReasoningFromCanonicalTest
{
    private static final String TEXT = "Let me think. ".repeat(400);

    private final LlmAnthropicReasoningFromCanonical reasoning = new LlmAnthropicReasoningFromCanonical(false, true);
    private final LlmAnthropicReasoningFromCanonical unsigned = new LlmAnthropicReasoningFromCanonical(true, true);
    private final LlmAnthropicReasoningFromCanonical foreign = new LlmAnthropicReasoningFromCanonical(false, false);
    private final LlmAnthropicReasoningToCanonical canonical = new LlmAnthropicReasoningToCanonical();

    @Test
    public void shouldConvertTextToThinkingBlock()
    {
        assertTransforms(reasoning,
            json("{'text':'Let me think.','anthropic:signature':'sig-1'}"),
            json("{'type':'thinking','thinking':'Let me think.','signature':'sig-1'}"));
    }

    @Test
    public void shouldConvertSignatureFirstToThinkingBlock()
    {
        assertTransforms(reasoning,
            json("{'anthropic:signature':'sig-1','text':'Let me think.'}"),
            json("{'type':'thinking','signature':'sig-1','thinking':'Let me think.'}"));
    }

    @Test
    public void shouldConvertDataToRedactedBlock()
    {
        assertTransforms(reasoning,
            json("{'anthropic:data':'opaque'}"),
            json("{'type':'redacted_thinking','data':'opaque'}"));
    }

    @Test
    public void shouldConvertTextWithoutNativeMembers()
    {
        assertTransforms(reasoning,
            json("{'text':'Let me think.'}"),
            json("{'type':'thinking','thinking':'Let me think.'}"));
    }

    @Test
    public void shouldDropOtherDialectPrefixedMembersAndRestoreOwn()
    {
        assertTransforms(reasoning,
            json("{'openai:note':'x','anthropic:cache_control':{'type':'ephemeral'},'text':'t'}"),
            json("{'cache_control':{'type':'ephemeral'},'type':'thinking','thinking':'t'}"));
    }

    @Test
    public void shouldAddEmptySignatureToUnsignedText()
    {
        assertTransforms(unsigned,
            json("{'text':'Let me think.'}"),
            json("{'type':'thinking','thinking':'Let me think.','signature':''}"));
    }

    @Test
    public void shouldKeepSignatureWhenUnsignedModeHasOne()
    {
        assertTransforms(unsigned,
            json("{'text':'Let me think.','anthropic:signature':'sig-1'}"),
            json("{'type':'thinking','thinking':'Let me think.','signature':'sig-1'}"));
    }

    @Test
    public void shouldNotAddSignatureToRedactedWhenUnsignedModeHasNone()
    {
        assertTransforms(unsigned,
            json("{'anthropic:data':'opaque'}"),
            json("{'type':'redacted_thinking','data':'opaque'}"));
    }

    @Test
    public void shouldRoundTripThinkingBlock()
    {
        assertRoundTrips(canonical, reasoning,
            json("{'type':'thinking','thinking':'Let me think.','signature':'sig-1'}"));
    }

    @Test
    public void shouldRoundTripThinkingBlockWithSignatureFirst()
    {
        assertRoundTrips(canonical, reasoning,
            json("{'type':'thinking','signature':'sig-1','thinking':'Let me think.'}"));
    }

    @Test
    public void shouldRoundTripThinkingBlockLargerThanWindow()
    {
        assertRoundTrips(canonical, reasoning,
            json("{'type':'thinking','thinking':'" + TEXT + "','signature':'sig-1'}"));
    }

    @Test
    public void shouldRoundTripRedactedBlock()
    {
        assertRoundTrips(canonical, reasoning,
            json("{'type':'redacted_thinking','data':'opaque'}"));
    }

    @Test
    public void shouldRejectBlockWithoutTextOrData()
    {
        assertRejects(reasoning, json("{'openai:note':'x'}"));
    }

    @Test
    public void shouldRejectTextThatIsNotAString()
    {
        assertRejects(reasoning, json("{'text':{'a':1}}"));
    }

    @Test
    public void shouldDropOwnPrefixedMembersAcrossDialects()
    {
        assertTransforms(foreign,
            json("{'text':'Let me think.','anthropic:signature':'sig-1'}"),
            json("{'type':'thinking','thinking':'Let me think.'}"));
    }

    @Test
    public void shouldPassUnprefixedMembersAcrossDialects()
    {
        assertTransforms(foreign,
            json("{'text':'Let me think.'}"),
            json("{'type':'thinking','thinking':'Let me think.'}"));
    }
}
