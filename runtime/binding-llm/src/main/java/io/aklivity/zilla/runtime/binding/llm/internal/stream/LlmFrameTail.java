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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

/**
 * The bytes a fan-out of a staged frame did not consume, kept until the next frame of the same section arrives.
 * <p>
 * The bytes are put back immediately in front of the next frame payload, in the space its dead header occupied,
 * so the fan-out sees them contiguous with the payload without holding a window of its own.
 */
final class LlmFrameTail
{
    private byte[] bytes = new byte[0];
    private int length;

    int length()
    {
        return length;
    }

    void keep(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        if (bytes.length < length)
        {
            bytes = new byte[Math.max(length, bytes.length << 1)];
        }

        buffer.getBytes(offset, bytes, 0, length);
        this.length = length;
    }

    void restore(
        MutableDirectBufferEx buffer,
        int limit)
    {
        buffer.putBytes(limit - length, bytes, 0, length);
    }

    void clear()
    {
        length = 0;
    }
}
