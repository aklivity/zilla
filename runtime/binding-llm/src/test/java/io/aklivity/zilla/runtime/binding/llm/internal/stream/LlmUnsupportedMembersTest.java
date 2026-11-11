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
    private static final int TYPE_TEXT_FOOTPRINT = 36;
    private static final String CHAT_HARMLESS =
        "{'openai/ccs:n':1,'openai/ccs:logprobs':false,'openai/ccs:audio':null,'openai/ccs:modalities':null}";
    private static final String RESPONSES_HARMLESS =
        "{'openai/rs:background':false,'openai/rs:previous_response_id':null,'openai/rs:conversation':null}";

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
    public void shouldAcceptJudgedValueExactlyAtCaptureLimit()
    {
        final String padding = "x".repeat(LlmToolTransform.HELD_MAX - TYPE_TEXT_FOOTPRINT);

        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("response_format"));

            assertTransforms(transform, json("{'x:response_format':{'type':'text','type':'" + padding + "'}}"), "{}");
            assertThat(transform.rejectedMember(), nullValue());
        }
    }

    @Test
    public void shouldRejectJudgedValueOneOverCaptureLimit()
    {
        final String padding = "x".repeat(LlmToolTransform.HELD_MAX - TYPE_TEXT_FOOTPRINT + 1);

        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("response_format"));

            assertRejects(transform, json("{'x:response_format':{'type':'text','type':'" + padding + "'}}"));
            assertThat(transform.rejectedMember(), equalTo("response_format"));
        }
    }

    @Test
    public void shouldRejectJudgedStringLongerThanCaptureLimit()
    {
        final String padding = "x".repeat(LlmToolTransform.HELD_MAX + 1);

        for (Function<LlmModelCapability, LlmToolTransform> dialect : DIALECTS)
        {
            final LlmToolTransform transform = dialect.apply(unsupported("tool_choice"));

            assertRejects(transform, json("{'x:tool_choice':'" + padding + "'}"));
            assertThat(transform.rejectedMember(), equalTo("tool_choice"));
        }
    }

    @Test
    public void shouldJudgeFirstTypeMemberOfResponseFormat()
    {
        assertDropped("response_format", "{'type':'text','type':'json_object'}");
        assertRejected("response_format", "{'type':'json_object','type':'text'}");
        assertRejected("response_format", "{'type':{'type':'text'}}");
        assertRejected("response_format", "{'extra':true,'type':'text'}");
        assertDropped("response_format", "{'type':'text','type':{'extra':true}}");
    }

    @Test
    public void shouldDropHarmlessBehaviouralMemberOfOtherVariant()
    {
        final LlmModelCapability capability = LlmModelCapability.UNSPECIFIED;

        assertTransforms(LlmOpenaiControlFromCanonical.members("openai/ccs", false, capability),
            json(RESPONSES_HARMLESS), "{}");
        assertTransforms(LlmOpenaiResponsesControlFromCanonical.members("openai/rs", capability),
            json(CHAT_HARMLESS), "{}");
        assertTransforms(LlmAnthropicControlFromCanonical.members(false, capability), json(CHAT_HARMLESS), "{}");
        assertTransforms(LlmAnthropicControlFromCanonical.members(false, capability), json(RESPONSES_HARMLESS), "{}");
        assertTransforms(LlmGeminiControlFromCanonical.members(false, capability), json(CHAT_HARMLESS), "{}");
        assertTransforms(LlmGeminiControlFromCanonical.members(false, capability), json(RESPONSES_HARMLESS), "{}");
    }

    @Test
    public void shouldRejectBehaviouralMemberOfOtherVariantCarryingBehaviourChange()
    {
        assertForeignRejected("openai/ccs:n", "2");
        assertForeignRejected("openai/ccs:n", "1.0");
        assertForeignRejected("openai/ccs:n", "'1'");
        assertForeignRejected("openai/ccs:logprobs", "true");
        assertForeignRejected("openai/ccs:logprobs", "0");
        assertForeignRejected("openai/ccs:audio", "false");
        assertForeignRejected("openai/ccs:modalities", "['text']");
        assertForeignRejected("openai/rs:background", "true");
        assertForeignRejected("openai/rs:previous_response_id", "'resp_1'");
        assertForeignRejected("openai/rs:conversation", "{'id':'c'}");
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

    private static void assertForeignRejected(
        String member,
        String value)
    {
        final LlmModelCapability capability = LlmModelCapability.UNSPECIFIED;
        final List<LlmToolTransform> transforms = member.startsWith("openai/ccs")
            ? List.of(LlmOpenaiResponsesControlFromCanonical.members("openai/rs", capability),
                LlmAnthropicControlFromCanonical.members(false, capability),
                LlmGeminiControlFromCanonical.members(false, capability))
            : List.of(LlmOpenaiControlFromCanonical.members("openai/ccs", false, capability),
                LlmAnthropicControlFromCanonical.members(false, capability),
                LlmGeminiControlFromCanonical.members(false, capability));

        for (LlmToolTransform transform : transforms)
        {
            assertRejects(transform, json("{'" + member + "':" + value + "}"));
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
