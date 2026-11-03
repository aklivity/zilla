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

final class LlmOpenaiMembers
{
    static final String VENDOR = "openai";

    private static final String[] SHARED =
    {
        "store",
        "metadata",
        "prompt_cache_key",
        "user",
        "include_obfuscation",
        "service_tier"
    };

    static String scope(
        String dialect,
        CharSequence member)
    {
        return shared(member) ? VENDOR : dialect;
    }

    private static boolean shared(
        CharSequence member)
    {
        boolean found = false;

        for (int index = 0; !found && index < SHARED.length; index++)
        {
            found = equal(member, SHARED[index]);
        }

        return found;
    }

    private static boolean equal(
        CharSequence member,
        String name)
    {
        boolean equal = member.length() == name.length();

        for (int index = 0; equal && index < name.length(); index++)
        {
            equal = member.charAt(index) == name.charAt(index);
        }

        return equal;
    }

    private LlmOpenaiMembers()
    {
    }
}
