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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmMembersOutput implements LlmSectionPipeline.Output
{
    private static final int NONE = -1;
    private static final DirectBufferEx BYTE_VALUES = new UnsafeBufferEx(byteValues());

    @FunctionalInterface
    interface Members
    {
        void put(
            DirectBufferEx buffer,
            int offset,
            int length);
    }

    private final Members members;

    private boolean opened;
    private int held;

    LlmMembersOutput(
        Members members)
    {
        this.members = members;
        this.held = NONE;
    }

    void begin()
    {
        opened = false;
        held = NONE;
    }

    @Override
    public void put(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        int from = offset;
        int remaining = length;

        if (!opened && remaining > 0)
        {
            opened = true;
            from++;
            remaining--;
        }

        if (remaining > 0)
        {
            final int tail = buffer.getByte(from + remaining - 1);

            if (held != NONE)
            {
                members.put(BYTE_VALUES, held & 0xFF, 1);
            }

            if (remaining > 1)
            {
                members.put(buffer, from, remaining - 1);
            }

            held = tail;
        }

        if (last)
        {
            held = NONE;
        }
    }

    private static byte[] byteValues()
    {
        final byte[] values = new byte[256];

        for (int value = 0; value < values.length; value++)
        {
            values[value] = (byte) value;
        }

        return values;
    }
}
