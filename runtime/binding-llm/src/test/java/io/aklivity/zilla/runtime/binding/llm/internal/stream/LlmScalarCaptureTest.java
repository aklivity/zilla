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
import static io.aklivity.zilla.runtime.binding.llm.internal.stream.LlmToolTransformRunner.json;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;

public class LlmScalarCaptureTest
{
    private static final int FITS = LlmToolTransform.HELD_MAX - 4;
    private static final int OVERFLOWS = FITS + 1;
    private static final int RESPONSES_FITS = 2048 - 4;
    private static final List<String> NOT_SCALARS = List.of("{}", "{'a':1}", "[]", "['x']", "[{'a':[1]}]");

    private static final LlmModelCapability CAPABILITY = LlmModelCapability.UNSPECIFIED;

    private static final List<Supplier<LlmToolTransform>> CANONICAL_EFFORT_AND_BUDGET = List.of(
        () -> LlmOpenaiControlFromCanonical.members("openai/ccs", false, CAPABILITY),
        () -> LlmOpenaiResponsesControlFromCanonical.members("openai/rs", CAPABILITY),
        () -> LlmAnthropicControlFromCanonical.members(false, CAPABILITY),
        () -> LlmGeminiControlFromCanonical.members(false, CAPABILITY));

    private static final List<Supplier<LlmToolTransform>> CANONICAL_PARALLEL_AND_FORMAT = List.of(
        () -> LlmAnthropicControlFromCanonical.members(false, CAPABILITY),
        () -> LlmGeminiControlFromCanonical.members(false, CAPABILITY));

    private static final List<Supplier<LlmToolTransform>> CANONICAL_FORMAT = List.of(
        () -> LlmOpenaiResponsesControlFromCanonical.members("openai/rs", CAPABILITY),
        () -> LlmAnthropicControlFromCanonical.members(false, CAPABILITY),
        () -> LlmGeminiControlFromCanonical.members(false, CAPABILITY));

    @Test
    public void shouldRejectReasoningEffortThatIsNotScalar()
    {
        for (String value : NOT_SCALARS)
        {
            assertRejectedNamed(CANONICAL_EFFORT_AND_BUDGET, "{'x:reasoning_effort':" + value + "}", "reasoning_effort");
        }
    }

    @Test
    public void shouldRejectThinkingBudgetThatIsNotScalar()
    {
        for (String value : NOT_SCALARS)
        {
            assertRejectedNamed(CANONICAL_EFFORT_AND_BUDGET, "{'x:thinking_budget_tokens':" + value + "}",
                "thinking_budget_tokens");
        }
    }

    @Test
    public void shouldRejectParallelToolCallsThatIsNotScalar()
    {
        for (String value : NOT_SCALARS)
        {
            assertRejectedNamed(CANONICAL_PARALLEL_AND_FORMAT, "{'x:parallel_tool_calls':" + value + "}",
                "parallel_tool_calls");
        }
    }

    @Test
    public void shouldRejectResponseFormatTypeThatIsNotScalar()
    {
        for (String value : NOT_SCALARS)
        {
            assertRejectedNamed(CANONICAL_FORMAT, "{'x:response_format':{'type':" + value + "}}", "response_format");
        }
    }

    @Test
    public void shouldRejectFormatTypeThatIsNotScalarWhenDecoding()
    {
        for (String value : NOT_SCALARS)
        {
            assertRejectedNamed(List.of(LlmAnthropicControlToCanonical::new),
                "{'output_config':{'format':{'type':" + value + ",'schema':{}}}}", "type");
            assertRejectedNamed(List.of(LlmOpenaiResponsesControlToCanonical::new),
                "{'text':{'format':{'type':" + value + "}}}", "type");
        }
    }

    @Test
    public void shouldNameMemberOfScalarFillingCaptureLimitExactly()
    {
        final String value = quoted(FITS);

        assertRejectedNamed(CANONICAL_EFFORT_AND_BUDGET, "{'x:reasoning_effort':" + value + "}", "reasoning_effort");
        assertRejectedNamed(CANONICAL_PARALLEL_AND_FORMAT, "{'x:parallel_tool_calls':" + value + "}", "parallel_tool_calls");
        assertRejectedNamed(CANONICAL_FORMAT, "{'x:response_format':{'type':" + value + "}}", "response_format");
        assertRejectedNamed(List.of(LlmAnthropicControlToCanonical::new),
            "{'output_config':{'format':{'type':" + value + ",'schema':{}}}}", "type");
        assertRejectedNamed(List.of(LlmOpenaiResponsesControlToCanonical::new),
            "{'text':{'format':{'type':" + quoted(RESPONSES_FITS) + "}}}", "type");
    }

    @Test
    public void shouldNotNameMemberOfScalarOverflowingCaptureLimit()
    {
        for (int length : List.of(OVERFLOWS, LlmToolTransform.HELD_MAX + 1))
        {
            final String overflow = quoted(length);

            assertRejectedNamed(CANONICAL_EFFORT_AND_BUDGET, "{'x:reasoning_effort':" + overflow + "}", null);
            assertRejectedNamed(CANONICAL_PARALLEL_AND_FORMAT, "{'x:parallel_tool_calls':" + overflow + "}", null);
            assertRejectedNamed(CANONICAL_FORMAT, "{'x:response_format':{'type':" + overflow + "}}", null);
            assertRejectedNamed(List.of(LlmAnthropicControlToCanonical::new),
                "{'output_config':{'format':{'type':" + overflow + ",'schema':{}}}}", null);
        }

        for (int length : List.of(RESPONSES_FITS + 1, 2048 + 1))
        {
            assertRejectedNamed(List.of(LlmOpenaiResponsesControlToCanonical::new),
                "{'text':{'format':{'type':" + quoted(length) + "}}}", null);
        }
    }

    @Test
    public void shouldNotNameMemberOfNumberOverflowingCaptureLimit()
    {
        final String digits = "1".repeat(OVERFLOWS);

        assertRejectedNamed(CANONICAL_EFFORT_AND_BUDGET, "{'x:thinking_budget_tokens':" + digits + "}", null);
    }

    private static String quoted(
        int length)
    {
        return "'" + "x".repeat(length) + "'";
    }

    private static void assertRejectedNamed(
        List<Supplier<LlmToolTransform>> transforms,
        String json,
        String member)
    {
        for (Supplier<LlmToolTransform> supplier : transforms)
        {
            final LlmToolTransform transform = supplier.get();

            assertRejects(transform, json(json));

            final String description = json.length() + " " + transform.getClass().getSimpleName();

            if (member != null)
            {
                assertThat(description, transform.rejectedMember(), equalTo(member));
            }
            else
            {
                assertThat(description, transform.rejectedMember(), nullValue());
            }
        }
    }
}
