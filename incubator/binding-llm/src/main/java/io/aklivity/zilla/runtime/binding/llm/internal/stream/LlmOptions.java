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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmOptions
{
    static final int NONE = -1;
    static final int VALUE_MAX = 4096;

    private final String[] members;
    private final byte[][] values;
    private final int[] lengths;
    private final boolean[] present;
    private final int[] order;

    private final UnsafeBufferEx view;

    private int pending;

    LlmOptions(
        String... members)
    {
        this.members = members;
        this.values = new byte[members.length][];
        this.lengths = new int[members.length];
        this.present = new boolean[members.length];
        this.order = new int[members.length];
        this.view = new UnsafeBufferEx();
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

    void begin(
        int slot)
    {
        if (!present[slot])
        {
            present[slot] = true;
            order[pending++] = slot;
        }

        lengths[slot] = 0;
    }

    boolean append(
        int slot,
        DirectBufferEx source,
        int offset,
        int length)
    {
        final int required = lengths[slot] + length;
        final boolean fits = required <= VALUE_MAX;

        if (fits)
        {
            if (values[slot] == null || values[slot].length < required)
            {
                final byte[] grown = new byte[Math.max(required, 64)];
                if (values[slot] != null)
                {
                    System.arraycopy(values[slot], 0, grown, 0, lengths[slot]);
                }
                values[slot] = grown;
            }

            source.getBytes(offset, values[slot], lengths[slot], length);
            lengths[slot] = required;
        }

        return fits;
    }

    int length(
        int slot)
    {
        return lengths[slot];
    }

    DirectBufferEx value(
        int slot)
    {
        view.wrap(values[slot], 0, lengths[slot]);

        return view;
    }

    String get(
        int slot)
    {
        return present[slot] ? new String(values[slot], 0, lengths[slot], UTF_8) : null;
    }

    int next()
    {
        return pending > 0 ? order[0] : NONE;
    }

    void remove(
        int slot)
    {
        if (present[slot])
        {
            present[slot] = false;
            lengths[slot] = 0;

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
