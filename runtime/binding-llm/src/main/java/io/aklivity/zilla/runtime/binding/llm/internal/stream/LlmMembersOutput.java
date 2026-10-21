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

    @FunctionalInterface
    interface Members
    {
        void put(
            DirectBufferEx buffer,
            int offset,
            int length);
    }

    private final Members members;
    private final UnsafeBufferEx heldBuffer;
    private final byte[] heldBytes;

    private boolean opened;
    private int held;

    LlmMembersOutput(
        Members members)
    {
        this.members = members;
        this.heldBytes = new byte[1];
        this.heldBuffer = new UnsafeBufferEx(heldBytes);
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
            if (held != NONE)
            {
                heldBytes[0] = (byte) held;
                members.put(heldBuffer, 0, 1);
            }

            if (remaining > 1)
            {
                members.put(buffer, from, remaining - 1);
            }

            held = buffer.getByte(from + remaining - 1);
        }

        if (last)
        {
            held = NONE;
        }
    }
}
