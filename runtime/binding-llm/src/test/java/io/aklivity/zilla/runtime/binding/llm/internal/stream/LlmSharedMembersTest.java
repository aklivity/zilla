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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class LlmSharedMembersTest
{
    @Test
    public void shouldPromoteOpenaiSamplingMembers()
    {
        for (String member : new String[] {"temperature", "top_p", "stop", "seed", "frequency_penalty", "presence_penalty"})
        {
            assertThat(member, LlmSharedMembers.openai(member), equalTo(member));
        }
    }

    @Test
    public void shouldKeepOpenaiMembersThatAreNotShared()
    {
        for (String member : new String[] {"top_k", "n", "logprobs", "top_logprobs", "logit_bias", "store", "temperatures"})
        {
            assertThat(member, LlmSharedMembers.openai(member), nullValue());
        }
    }

    @Test
    public void shouldPromoteOpenaiResponsesSamplingMembers()
    {
        assertThat(LlmSharedMembers.openaiResponses("temperature"), equalTo("temperature"));
        assertThat(LlmSharedMembers.openaiResponses("top_p"), equalTo("top_p"));
        assertThat(LlmSharedMembers.openaiResponses("stop"), nullValue());
        assertThat(LlmSharedMembers.openaiResponses("seed"), nullValue());
    }

    @Test
    public void shouldPromoteAnthropicSamplingMembers()
    {
        assertThat(LlmSharedMembers.anthropic("temperature"), equalTo("temperature"));
        assertThat(LlmSharedMembers.anthropic("top_p"), equalTo("top_p"));
        assertThat(LlmSharedMembers.anthropic("top_k"), equalTo("top_k"));
        assertThat(LlmSharedMembers.anthropic("stop_sequences"), equalTo("stop"));
        assertThat(LlmSharedMembers.anthropic("stop"), nullValue());
        assertThat(LlmSharedMembers.anthropic("thinking"), nullValue());
    }

    @Test
    public void shouldPromoteGeminiGenerationConfigLeaves()
    {
        assertThat(LlmSharedMembers.gemini("temperature"), equalTo("temperature"));
        assertThat(LlmSharedMembers.gemini("topP"), equalTo("top_p"));
        assertThat(LlmSharedMembers.gemini("top_p"), equalTo("top_p"));
        assertThat(LlmSharedMembers.gemini("topK"), equalTo("top_k"));
        assertThat(LlmSharedMembers.gemini("top_k"), equalTo("top_k"));
        assertThat(LlmSharedMembers.gemini("stopSequences"), equalTo("stop"));
        assertThat(LlmSharedMembers.gemini("stop_sequences"), equalTo("stop"));
        assertThat(LlmSharedMembers.gemini("seed"), equalTo("seed"));
        assertThat(LlmSharedMembers.gemini("frequencyPenalty"), equalTo("frequency_penalty"));
        assertThat(LlmSharedMembers.gemini("frequency_penalty"), equalTo("frequency_penalty"));
        assertThat(LlmSharedMembers.gemini("presencePenalty"), equalTo("presence_penalty"));
        assertThat(LlmSharedMembers.gemini("presence_penalty"), equalTo("presence_penalty"));
    }

    @Test
    public void shouldKeepGeminiGenerationConfigResidue()
    {
        for (String member : new String[] {"candidateCount", "maxOutputTokens", "max_output_tokens", "responseMimeType",
            "thinkingConfig", "stop", "topp", "temperatures"})
        {
            assertThat(member, LlmSharedMembers.gemini(member), nullValue());
        }
    }

    @Test
    public void shouldPromoteOpenaiToolMembers()
    {
        assertThat(LlmSharedMembers.openai("tool_choice"), equalTo("tool_choice"));
        assertThat(LlmSharedMembers.openai("parallel_tool_calls"), equalTo("parallel_tool_calls"));
    }

    @Test
    public void shouldPromoteOpenaiResponsesParallelToolCallsOnly()
    {
        assertThat(LlmSharedMembers.openaiResponses("parallel_tool_calls"), equalTo("parallel_tool_calls"));
        assertThat(LlmSharedMembers.openaiResponses("tool_choice"), nullValue());
    }

    @Test
    public void shouldConvertAnthropicToolChoiceInsteadOfRenamingIt()
    {
        assertThat(LlmSharedMembers.anthropic("tool_choice"), nullValue());
        assertThat(LlmSharedMembers.anthropic("parallel_tool_calls"), nullValue());
    }

    @Test
    public void shouldConvertGeminiToolConfigInsteadOfRenamingIt()
    {
        assertThat(LlmSharedMembers.gemini("toolConfig"), nullValue());
        assertThat(LlmSharedMembers.gemini("tool_config"), nullValue());
    }
}
