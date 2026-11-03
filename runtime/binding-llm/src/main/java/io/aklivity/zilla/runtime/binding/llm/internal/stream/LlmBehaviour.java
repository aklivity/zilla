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

import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmBehaviour
{
    private static final char PREFIX_END = ':';
    private static final String CHAT_COMPLETIONS = LlmOpenaiDialectFactorySpi.NAME;
    private static final String RESPONSES = LlmOpenaiResponsesDialectFactorySpi.NAME;
    private static final String MEMBER_N = "n";
    private static final String MEMBER_LOGPROBS = "logprobs";
    private static final String MEMBER_BACKGROUND = "background";
    private static final String[] CHAT_COMPLETIONS_MEMBERS = {MEMBER_N, MEMBER_LOGPROBS, "audio", "modalities"};
    private static final String[] RESPONSES_MEMBERS = {"previous_response_id", MEMBER_BACKGROUND, "conversation"};

    static boolean listed(
        CharSequence key)
    {
        final int colon = indexOf(key, PREFIX_END);

        return colon > 0 &&
            (listed(key, colon, CHAT_COMPLETIONS, CHAT_COMPLETIONS_MEMBERS) ||
            listed(key, colon, RESPONSES, RESPONSES_MEMBERS));
    }

    static boolean harmless(
        CharSequence key,
        LlmCapturedValue value)
    {
        final int from = indexOf(key, PREFIX_END) + 1;
        final JsonEvent event = value.event(0);

        return event == JsonEvent.VALUE_NULL ||
            region(key, from, MEMBER_N) && event == JsonEvent.VALUE_NUMBER && "1".equals(value.text(0)) ||
            (region(key, from, MEMBER_LOGPROBS) || region(key, from, MEMBER_BACKGROUND)) && event == JsonEvent.VALUE_FALSE;
    }

    private static boolean listed(
        CharSequence key,
        int colon,
        String prefix,
        String[] members)
    {
        boolean listed = false;

        if (region(key, 0, colon, prefix))
        {
            for (int index = 0; !listed && index < members.length; index++)
            {
                listed = region(key, colon + 1, members[index]);
            }
        }

        return listed;
    }

    private static boolean region(
        CharSequence key,
        int from,
        String expected)
    {
        return region(key, from, key.length(), expected);
    }

    private static boolean region(
        CharSequence key,
        int from,
        int to,
        String expected)
    {
        boolean matches = to - from == expected.length();

        for (int index = 0; matches && index < expected.length(); index++)
        {
            matches = key.charAt(from + index) == expected.charAt(index);
        }

        return matches;
    }

    private static int indexOf(
        CharSequence key,
        char expected)
    {
        int index = 0;

        while (index < key.length() && key.charAt(index) != expected)
        {
            index++;
        }

        return index < key.length() ? index : -1;
    }

    private LlmBehaviour()
    {
    }
}
