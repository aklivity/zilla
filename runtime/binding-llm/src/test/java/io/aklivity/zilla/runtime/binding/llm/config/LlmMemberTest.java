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
package io.aklivity.zilla.runtime.binding.llm.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;

import org.junit.Test;

public class LlmMemberTest
{
    @Test
    public void shouldResolveMembersByName()
    {
        assertThat(LlmMember.of("temperature"), equalTo(LlmMember.TEMPERATURE));
        assertThat(LlmMember.of("parallel_tool_calls"), equalTo(LlmMember.PARALLEL_TOOL_CALLS));
        assertThat(LlmMember.of("service_tier"), equalTo(LlmMember.SERVICE_TIER));
    }

    @Test
    public void shouldNotResolveUnknownMember()
    {
        assertThat(LlmMember.of("logit_bias"), equalTo(LlmMember.INVALID));
        assertThat(LlmMember.of(""), equalTo(LlmMember.INVALID));
        assertThat(LlmMember.of("TEMPERATURE"), equalTo(LlmMember.INVALID));
    }

    @Test
    public void shouldNameMembers()
    {
        for (int member = 0; member < LlmMember.COUNT; member++)
        {
            assertThat(LlmMember.of(LlmMember.nameOf(member)), equalTo(member));
        }
    }

    @Test
    public void shouldClassifyBehaviouralMembers()
    {
        assertThat(LlmMember.behavioural(LlmMember.STOP), equalTo(true));
        assertThat(LlmMember.behavioural(LlmMember.TOOL_CHOICE), equalTo(true));
        assertThat(LlmMember.behavioural(LlmMember.PARALLEL_TOOL_CALLS), equalTo(true));
        assertThat(LlmMember.behavioural(LlmMember.RESPONSE_FORMAT), equalTo(true));
    }

    @Test
    public void shouldClassifyAdvisoryMembers()
    {
        assertThat(LlmMember.behavioural(LlmMember.TEMPERATURE), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.TOP_P), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.TOP_K), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.SEED), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.FREQUENCY_PENALTY), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.PRESENCE_PENALTY), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.REASONING_EFFORT), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.THINKING_BUDGET_TOKENS), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.USER), equalTo(false));
        assertThat(LlmMember.behavioural(LlmMember.SERVICE_TIER), equalTo(false));
    }

    @Test
    public void shouldMaskMembers()
    {
        assertThat(LlmMember.maskOf(List.of()), equalTo(0));
        assertThat(LlmMember.maskOf(List.of("temperature", "stop")), equalTo(0b1001));
        assertThat(LlmMember.maskOf(List.of("unknown", "top_p")), equalTo(0b0010));
    }

    @Test
    public void shouldGroupMembersInListedOrder()
    {
        assertThat(LlmMember.groupOf(List.of("thinking_budget_tokens", "reasoning_effort")),
            equalTo(new int[] {LlmMember.THINKING_BUDGET_TOKENS, LlmMember.REASONING_EFFORT}));
        assertThat(LlmMember.groupOf(List.of("unknown", "seed")), equalTo(new int[] {LlmMember.SEED}));
    }
}
