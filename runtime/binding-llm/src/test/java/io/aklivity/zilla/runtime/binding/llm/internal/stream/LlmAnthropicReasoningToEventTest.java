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

public class LlmAnthropicReasoningToEventTest
{
    private static final String SIGNATURE = "sig-1".repeat(800);

    private final LlmAnthropicReasoningToEvent signature = LlmAnthropicReasoningToEvent.signature();
    private final LlmAnthropicReasoningToEvent redacted = LlmAnthropicReasoningToEvent.redacted();

    @Test
    public void shouldConvertSignatureToSignatureDelta()
    {
        signature.index(2);

        assertTransforms(signature,
            json("{'anthropic:signature':'sig-1'}"),
            json("{'type':'content_block_delta','index':2,'delta':{'type':'signature_delta','signature':'sig-1'}}"));
    }

    @Test
    public void shouldConvertSignatureLargerThanWindow()
    {
        assertTransforms(signature,
            json("{'anthropic:signature':'" + SIGNATURE + "'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'signature_delta','signature':'" +
                SIGNATURE + "'}}"));
    }

    @Test
    public void shouldDropOtherMembersAfterText()
    {
        assertTransforms(signature,
            json("{'text':'t','openai:note':{'a':[1,2]},'anthropic:signature':'sig-1','anthropic:cache_control':'x'}"),
            json("{'type':'content_block_delta','index':0,'delta':{'type':'signature_delta','signature':'sig-1'}}"));
    }

    @Test
    public void shouldProduceNothingWithoutSignature()
    {
        assertTransforms(signature, json("{'text':'t'}"), "");
    }

    @Test
    public void shouldConvertDataToRedactedThinkingStart()
    {
        redacted.index(1);

        assertTransforms(redacted,
            json("{'anthropic:data':'opaque'}"),
            json("{'type':'content_block_start','index':1,'content_block':{'type':'redacted_thinking','data':'opaque'}}"));
    }

    @Test
    public void shouldProduceNothingWithoutData()
    {
        assertTransforms(redacted, json("{'text':'t','anthropic:signature':'sig-1'}"), "");
    }
}
