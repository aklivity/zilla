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

final class LlmOptions
{
    static final int NONE = -1;

    private final String[] members;
    private final String[] literals;
    private final int[] order;

    private int pending;

    LlmOptions(
        String... members)
    {
        this.members = members;
        this.literals = new String[members.length];
        this.order = new int[members.length];
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

    String member(
        int slot)
    {
        return members[slot];
    }

    void set(
        int slot,
        String literal)
    {
        if (literals[slot] == null)
        {
            order[pending++] = slot;
        }

        literals[slot] = literal;
    }

    String get(
        int slot)
    {
        return literals[slot];
    }

    int next()
    {
        return pending > 0 ? order[0] : NONE;
    }

    void remove(
        int slot)
    {
        if (literals[slot] != null)
        {
            literals[slot] = null;

            int index = 0;
            while (order[index] != slot)
            {
                index++;
            }

            System.arraycopy(order, index + 1, order, index, pending - index - 1);
            pending--;
        }
    }

    boolean isEmpty()
    {
        return pending == 0;
    }
}
