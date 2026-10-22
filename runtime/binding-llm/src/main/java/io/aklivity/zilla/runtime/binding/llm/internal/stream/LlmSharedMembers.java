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

final class LlmSharedMembers
{
    static final String TEMPERATURE = "temperature";
    static final String TOP_P = "top_p";
    static final String TOP_K = "top_k";
    static final String STOP = "stop";
    static final String SEED = "seed";
    static final String FREQUENCY_PENALTY = "frequency_penalty";
    static final String PRESENCE_PENALTY = "presence_penalty";
    static final String TOOL_CHOICE = "tool_choice";
    static final String PARALLEL_TOOL_CALLS = "parallel_tool_calls";
    static final String RESPONSE_FORMAT = "response_format";
    static final String REASONING_EFFORT = "reasoning_effort";
    static final String THINKING_BUDGET_TOKENS = "thinking_budget_tokens";
    static final String USER = "user";
    static final String SERVICE_TIER = "service_tier";
    static final String SIGNATURE = "signature";
    static final String REDACTED_DATA = "redacted_data";

    private static final String NATIVE_STOP_SEQUENCES = "stop_sequences";
    private static final String NATIVE_SAFETY_IDENTIFIER = "safety_identifier";
    private static final String[][] OPENAI = {
        {TEMPERATURE, TEMPERATURE},
        {TOP_P, TOP_P},
        {STOP, STOP},
        {SEED, SEED},
        {FREQUENCY_PENALTY, FREQUENCY_PENALTY},
        {PRESENCE_PENALTY, PRESENCE_PENALTY},
        {TOOL_CHOICE, TOOL_CHOICE},
        {PARALLEL_TOOL_CALLS, PARALLEL_TOOL_CALLS},
        {RESPONSE_FORMAT, RESPONSE_FORMAT},
        {REASONING_EFFORT, REASONING_EFFORT},
        {NATIVE_SAFETY_IDENTIFIER, USER}
    };
    private static final String[][] OPENAI_RESPONSES = {
        {TEMPERATURE, TEMPERATURE},
        {TOP_P, TOP_P},
        {PARALLEL_TOOL_CALLS, PARALLEL_TOOL_CALLS},
        {NATIVE_SAFETY_IDENTIFIER, USER}
    };
    private static final String[][] ANTHROPIC = {
        {TEMPERATURE, TEMPERATURE},
        {TOP_P, TOP_P},
        {TOP_K, TOP_K}
    };
    private static final String[][] GEMINI = {
        {TEMPERATURE, TEMPERATURE},
        {"topP", TOP_P},
        {TOP_P, TOP_P},
        {"topK", TOP_K},
        {TOP_K, TOP_K},
        {"stopSequences", STOP},
        {NATIVE_STOP_SEQUENCES, STOP},
        {SEED, SEED},
        {"frequencyPenalty", FREQUENCY_PENALTY},
        {FREQUENCY_PENALTY, FREQUENCY_PENALTY},
        {"presencePenalty", PRESENCE_PENALTY},
        {PRESENCE_PENALTY, PRESENCE_PENALTY}
    };

    static String openai(
        CharSequence key)
    {
        return paired(key, OPENAI);
    }

    static String openaiResponses(
        CharSequence key)
    {
        return paired(key, OPENAI_RESPONSES);
    }

    static String anthropic(
        CharSequence key)
    {
        return matches(key, NATIVE_STOP_SEQUENCES) ? STOP : paired(key, ANTHROPIC);
    }

    static String gemini(
        CharSequence key)
    {
        return paired(key, GEMINI);
    }

    static boolean matches(
        CharSequence value,
        String expected)
    {
        final int length = expected.length();
        boolean matches = value.length() == length;

        for (int index = 0; matches && index < length; index++)
        {
            matches = value.charAt(index) == expected.charAt(index);
        }

        return matches;
    }

    private static String paired(
        CharSequence key,
        String[][] members)
    {
        String shared = null;

        for (int index = 0; shared == null && index < members.length; index++)
        {
            shared = matches(key, members[index][0]) ? members[index][1] : null;
        }

        return shared;
    }

    private LlmSharedMembers()
    {
    }
}
