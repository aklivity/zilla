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

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

// Hands chars to a consumer that takes UTF-8 bytes. Only the consumers that have not moved to chars use it, and it
// goes with the last of them.
final class LlmUtf8Output implements LlmTextPipeline.CharOutput
{
    private static final char REPLACEMENT_CHARACTER = '\ufffd';

    private final LlmTextPipeline.Output output;
    private final byte[] initialBytes;
    private final UnsafeBufferEx initialBuffer;

    private byte[] bytes;
    private UnsafeBufferEx buffer;

    LlmUtf8Output(
        LlmTextPipeline.Output output)
    {
        this.output = output;
        this.initialBytes = new byte[1024];
        this.initialBuffer = new UnsafeBufferEx(initialBytes);
        this.bytes = initialBytes;
        this.buffer = initialBuffer;
    }

    @Override
    public void put(
        CharSequence chars,
        int from,
        int to)
    {
        final int length = to - from;

        if (bytes.length < length * 3)
        {
            bytes = new byte[length * 3];
            buffer = new UnsafeBufferEx(bytes);
        }

        int position = 0;
        int index = from;

        while (index < to)
        {
            final char c = chars.charAt(index++);

            if (Character.isHighSurrogate(c) && index < to && Character.isLowSurrogate(chars.charAt(index)))
            {
                position = putCodePoint(Character.toCodePoint(c, chars.charAt(index)), position);
                index++;
            }
            else if (Character.isSurrogate(c))
            {
                position = putCodePoint(REPLACEMENT_CHARACTER, position);
            }
            else
            {
                position = putCodePoint(c, position);
            }
        }

        output.put(buffer, 0, position);

        if (bytes != initialBytes)
        {
            bytes = initialBytes;
            buffer = initialBuffer;
        }
    }

    private int putCodePoint(
        int codePoint,
        int position)
    {
        int next = position;

        if (codePoint < 0x80)
        {
            bytes[next++] = (byte) codePoint;
        }
        else if (codePoint < 0x800)
        {
            bytes[next++] = (byte) (0xC0 | codePoint >> 6);
            bytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }
        else if (codePoint < 0x10000)
        {
            bytes[next++] = (byte) (0xE0 | codePoint >> 12);
            bytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
            bytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }
        else
        {
            bytes[next++] = (byte) (0xF0 | codePoint >> 18);
            bytes[next++] = (byte) (0x80 | codePoint >> 12 & 0x3F);
            bytes[next++] = (byte) (0x80 | codePoint >> 6 & 0x3F);
            bytes[next++] = (byte) (0x80 | codePoint & 0x3F);
        }

        return next;
    }
}
