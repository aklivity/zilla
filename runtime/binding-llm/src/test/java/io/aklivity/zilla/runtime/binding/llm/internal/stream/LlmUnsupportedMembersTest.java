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

import static io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability.UNSET;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertRejects;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.assertTransforms;
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.function.Function;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.config.LlmMember;
import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier;

public class LlmUnsupportedMembersTest
{
    private static final String HUGE_SCHEMA = "x".repeat(5000);

    private static final List<Function<LlmModelCapability, LlmToolTransform>> DIALECTS = List.of(
        capability -> LlmOpenaiControlFromCanonical.members("openai/ccs", false, capability),
        capability -> LlmOpenaiResponsesControlFromCanonical.members("openai/rs", capability),
        capability -> LlmAnthropicControlFromCanonical.members(false, capability),
        capability -> LlmGeminiControlFromCanonical.members(false, capability));

    @Test
    public void shouldRejectBehaviouralMemberCarryingBehaviourChange()
    {
        assertRejected("parallel_tool_calls", "false");
        assertRejected("tool_choice", "'required'");
        assertRejected("tool_choice", "'none'");
        assertRejected("tool_choice", "{'type':'function','function':{'name':'f'}}");
        assertRejected("response_format", "{'type':'json_object'}");
        assertRejected("response_format", "{'type':'json_schema','json_schema':{'name':'n','schema':{}}}");
        assertRejected("stop", "['END']");
        assertRejected("stop", "'END'");
    }

    @Test
    public void shouldRejectBehaviouralMemberWhoseValueIsNotUnderstood()
    {
        assertRejected("parallel_tool_calls", "'yes'");
        assertRejected("parallel_tool_calls", "1");
        assertRejected("tool_choice", "'Auto'");
        assertRejected("tool_choice", "{}");
        assertRejected("response_format", "{}");
        assertRejected("response_format", "{'type':'text','extra':true}");
        assertRejected("response_format", "'text'");
    }

    @Test
    public void shouldDropBehaviouralMemberCarryingHarmlessValue()
    {
        assertDropped("parallel_tool_calls", "true");
        assertDropped("tool_choice", "'auto'");
        assertDropped("response_format", "{'type':'text'}");
    }

    @Test
    public void shouldDropBehaviouralMemberCarryingNull()
    {
        assertDropped("parallel_tool_calls", "null");
        assertDropped("tool_choice", "null");
        assertDropped("response_format", "null");
        assertDropped("stop", "null");
    }

    @Test
    public void shouldDropAdvisoryMember()
    {
        assertDropped("temperature", "0.7");
        assertDropped("top_p", "0.9");
        assertDropped("top_k", "40");
        assertDropped("seed", "42");
        assertDropped("frequency_penalty", "0.5");
        assertDropped("presence_penalty", "0.5");
        assertDropped("reasoning_effort", "'high'");
        assertDropped("thinking_budget_tokens", "2048");
        assertDropped("user", "'user-1'");
        assertDropped("service_tier", "'flex'");
    }

    @Test
    public void shouldNameRejectedMemberWhenValueExceedsHold()
    {
        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("response_format"));

            assertRejects(transform, json("{'x:response_format':{'type':'json_schema','json_schema':{'name':'n','schema':{'d':'" +
                HUGE_SCHEMA + "'}}}}"));
            assertThat(transform.rejectedMember(), equalTo("response_format"));
        }
    }

    @Test
    public void shouldNotNameMemberAfterHarmlessValue()
    {
        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("parallel_tool_calls", "tool_choice"));

            assertTransforms(transform, json("{'x:parallel_tool_calls':true,'x:tool_choice':'auto'}"), "{}");
            assertThat(transform.rejectedMember(), nullValue());
        }
    }

    @Test
    public void shouldRejectMemberAfterDroppedMembers()
    {
        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("seed", "user", "parallel_tool_calls"));

            assertRejects(transform, json("{'x:seed':1,'x:user':'u','x:parallel_tool_calls':false}"));
            assertThat(transform.rejectedMember(), equalTo("parallel_tool_calls"));
        }
    }

    @Test
    public void shouldKeepDroppingAfterHarmlessMember()
    {
        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("parallel_tool_calls", "seed"));

            assertTransforms(transform, json("{'x:parallel_tool_calls':true,'x:seed':1}"), "{}");
        }
    }

    @Test
    public void shouldNotApplyMembersThatAreNotListed()
    {
        final LlmModelCapability capability = unsupported("seed");

        assertTransforms(LlmOpenaiControlFromCanonical.members("openai/ccs", false, capability),
            json("{'x:seed':1,'x:parallel_tool_calls':false}"),
            json("{'parallel_tool_calls':false}"));
        assertTransforms(LlmOpenaiResponsesControlFromCanonical.members("openai/rs", capability),
            json("{'x:seed':1,'x:parallel_tool_calls':false}"),
            json("{'parallel_tool_calls':false}"));
        assertTransforms(LlmAnthropicControlFromCanonical.members(false, capability),
            json("{'x:seed':1,'x:top_k':5}"),
            json("{'top_k':5}"));
        assertTransforms(LlmGeminiControlFromCanonical.members(false, capability),
            json("{'x:seed':1,'x:top_k':5}"),
            json("{'generationConfig':{'topK':5}}"));
    }

    @Test
    public void shouldNotListAnythingByDefault()
    {
        assertTransforms(LlmOpenaiControlFromCanonical.members("openai/ccs", false, LlmModelCapability.UNSPECIFIED),
            json("{'x:parallel_tool_calls':false,'x:response_format':{'type':'json_object'},'x:tool_choice':'auto'}"),
            json("{'parallel_tool_calls':false,'response_format':{'type':'json_object'},'tool_choice':'auto'}"));
    }

    private static void assertRejected(
        String member,
        String value)
    {
        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported(member));

            assertRejects(transform, json("{'x:" + member + "':" + value + "}"));
            assertThat(member + " " + value, transform.rejectedMember(), equalTo(member));
        }
    }

    private static void assertDropped(
        String member,
        String value)
    {
        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported(member));

            assertTransforms(transform, json("{'x:" + member + "':" + value + "}"), "{}");
            assertThat(member + " " + value, transform.rejectedMember(), nullValue());
        }
    }

    private static LlmModelCapability unsupported(
        String... members)
    {
        return new LlmModelCapability(true, null, UNSET, UNSET, null, LlmServiceTier.ALL, LlmMember.maskOf(List.of(members)),
            new int[0][]);
    }
}
