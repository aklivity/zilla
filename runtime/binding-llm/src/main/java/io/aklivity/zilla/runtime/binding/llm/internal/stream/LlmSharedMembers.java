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

    private static final String NATIVE_STOP_SEQUENCES = "stop_sequences";
    private static final String[] OPENAI = {TEMPERATURE, TOP_P, STOP, SEED, FREQUENCY_PENALTY, PRESENCE_PENALTY};
    private static final String[] OPENAI_RESPONSES = {TEMPERATURE, TOP_P};
    private static final String[] ANTHROPIC = {TEMPERATURE, TOP_P, TOP_K};
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
        return named(key, OPENAI);
    }

    static String openaiResponses(
        CharSequence key)
    {
        return named(key, OPENAI_RESPONSES);
    }

    static String anthropic(
        CharSequence key)
    {
        return matches(key, NATIVE_STOP_SEQUENCES) ? STOP : named(key, ANTHROPIC);
    }

    static String gemini(
        CharSequence key)
    {
        String shared = null;

        for (int index = 0; shared == null && index < GEMINI.length; index++)
        {
            shared = matches(key, GEMINI[index][0]) ? GEMINI[index][1] : null;
        }

        return shared;
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

    private static String named(
        CharSequence key,
        String[] members)
    {
        String named = null;

        for (int index = 0; named == null && index < members.length; index++)
        {
            named = matches(key, members[index]) ? members[index] : null;
        }

        return named;
    }

    private LlmSharedMembers()
    {
    }
}
