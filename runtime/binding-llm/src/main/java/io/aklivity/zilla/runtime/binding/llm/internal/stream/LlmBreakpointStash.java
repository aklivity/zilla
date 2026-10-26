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
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class LlmBreakpointStash
{
    static final int CAPACITY = 128;

    private static final byte[] PREFIX = "\"prompt_cache_breakpoint\":{\"mode\":\"explicit\"".getBytes(UTF_8);
    private static final byte[] TTL = ",\"ttl\":\"".getBytes(UTF_8);
    private static final int TTL_MAX = 16;

    private final MutableDirectBufferEx buffer;

    private int length;

    LlmBreakpointStash()
    {
        this.buffer = new UnsafeBufferEx(new byte[CAPACITY]);
    }

    boolean present()
    {
        return length > 0;
    }

    int length()
    {
        return length;
    }

    DirectBufferEx buffer()
    {
        return buffer;
    }

    void clear()
    {
        length = 0;
    }

    void copyFrom(
        LlmBreakpointStash source)
    {
        buffer.putBytes(0, source.buffer, 0, source.length);
        length = source.length;
    }

    void set(
        CharSequence ttl)
    {
        int next = 0;

        buffer.putBytes(next, PREFIX);
        next += PREFIX.length;

        if (plain(ttl))
        {
            buffer.putBytes(next, TTL);
            next += TTL.length;

            for (int index = 0; index < ttl.length(); index++)
            {
                buffer.putByte(next++, (byte) ttl.charAt(index));
            }

            buffer.putByte(next++, (byte) '"');
        }

        buffer.putByte(next++, (byte) '}');
        length = next;
    }

    private static boolean plain(
        CharSequence ttl)
    {
        final int count = ttl != null ? ttl.length() : 0;
        boolean plain = count > 0 && count <= TTL_MAX;

        for (int index = 0; plain && index < count; index++)
        {
            final char value = ttl.charAt(index);

            plain = value >= '0' && value <= '9' || value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z' || value == '.';
        }

        return plain;
    }
}
