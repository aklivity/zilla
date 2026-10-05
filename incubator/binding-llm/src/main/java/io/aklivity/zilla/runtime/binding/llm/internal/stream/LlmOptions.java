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

import static java.nio.charset.StandardCharsets.UTF_8;

final class LlmOptions
{
    static final int NONE = -1;

    private final String[] members;
    private final String[] literals;

    private int present;

    LlmOptions(
        String... members)
    {
        this.members = members;
        this.literals = new String[members.length];
    }

    int slot(
        CharSequence member)
    {
        int slot = NONE;

        for (int index = 0; slot == NONE && index < members.length; index++)
        {
            if (members[index].contentEquals(member))
            {
                slot = index;
            }
        }

        return slot;
    }

    void set(
        int slot,
        String literal)
    {
        if (literals[slot] == null)
        {
            present++;
        }

        literals[slot] = literal;
    }

    String get(
        int slot)
    {
        return literals[slot];
    }

    boolean isEmpty()
    {
        return present == 0;
    }

    void clear()
    {
        for (int slot = 0; slot < literals.length; slot++)
        {
            literals[slot] = null;
        }

        present = 0;
    }

    byte[] members(
        boolean leadingComma)
    {
        final StringBuilder json = new StringBuilder();

        for (int slot = 0; slot < literals.length; slot++)
        {
            if (literals[slot] != null)
            {
                if (leadingComma || json.length() > 0)
                {
                    json.append(',');
                }

                json.append('"').append(members[slot]).append("\":").append(literals[slot]);
            }
        }

        return json.toString().getBytes(UTF_8);
    }
}
