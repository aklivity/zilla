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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmBehaviourTest
{
    @Test
    public void shouldListBehaviouralMembersOfResponses()
    {
        assertTrue(LlmBehaviour.listed("openai/rs:previous_response_id"));
        assertTrue(LlmBehaviour.listed("openai/rs:background"));
        assertTrue(LlmBehaviour.listed("openai/rs:conversation"));
    }

    @Test
    public void shouldListBehaviouralMembersOfChatCompletions()
    {
        assertTrue(LlmBehaviour.listed("openai/ccs:n"));
        assertTrue(LlmBehaviour.listed("openai/ccs:logprobs"));
        assertTrue(LlmBehaviour.listed("openai/ccs:audio"));
        assertTrue(LlmBehaviour.listed("openai/ccs:modalities"));
    }

    @Test
    public void shouldNotListMemberOfOtherPrefix()
    {
        assertFalse(LlmBehaviour.listed("openai/ccs:previous_response_id"));
        assertFalse(LlmBehaviour.listed("openai/rs:n"));
        assertFalse(LlmBehaviour.listed("openai:background"));
        assertFalse(LlmBehaviour.listed("anthropic:n"));
    }

    @Test
    public void shouldNotListMemberThatIsNotBehavioural()
    {
        assertFalse(LlmBehaviour.listed("openai/rs:truncation"));
        assertFalse(LlmBehaviour.listed("openai/ccs:logit_bias"));
        assertFalse(LlmBehaviour.listed("openai/rs:nn"));
        assertFalse(LlmBehaviour.listed("openai/rs:"));
    }

    @Test
    public void shouldNotListMemberWithoutPrefix()
    {
        assertFalse(LlmBehaviour.listed("n"));
        assertFalse(LlmBehaviour.listed("x:n"));
        assertFalse(LlmBehaviour.listed(""));
    }
}
