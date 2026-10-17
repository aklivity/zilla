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
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;

final class LlmReasoningDeliver extends LlmToolTransform
{
    private static final String MEMBER_TEXT = "text";
    private static final String MEMBER_SIGNATURE = "anthropic:signature";
    private static final String MEMBER_DATA = "anthropic:data";
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    @FunctionalInterface
    interface Listener
    {
        void onChunk(
            String member,
            DirectBufferEx buffer,
            int offset,
            int length,
            boolean last);
    }

    private final Listener listener;

    private byte[] bytes;
    private UnsafeBufferEx buffer;
    private char highSurrogate;

    LlmReasoningDeliver(
        Listener listener)
    {
        this.listener = listener;
        this.bytes = new byte[256];
        this.buffer = new UnsafeBufferEx(bytes);
    }

    @Override
    public void reset()
    {
        super.reset();
        highSurrogate = 0;
    }

    @Override
    protected int member(
        int depth,
        CharSequence key)
    {
        int action = DROP;

        if (matches(key, MEMBER_TEXT))
        {
            target = MEMBER_TEXT;
            action = DELIVER_STRING;
        }
        else if (matches(key, MEMBER_SIGNATURE))
        {
            target = MEMBER_SIGNATURE;
            action = DELIVER_STRING;
        }
        else if (matches(key, MEMBER_DATA))
        {
            target = MEMBER_DATA;
            action = DELIVER_STRING;
        }

        return action;
    }

    @Override
    protected void onOpen()
    {
    }

    @Override
    protected Status onClose(
        int depth)
    {
        pass(JsonEvent.END_OBJECT);

        return Status.ADVANCED;
    }

    @Override
    protected void deliver(
        String member,
        CharSequence chars,
        boolean last)
    {
        final int length = encode(chars, last);

        listener.onChunk(member, buffer, 0, length, last);
    }

    private int encode(
        CharSequence chars,
        boolean last)
    {
        final int length = chars.length();

        if (bytes.length < length * 3 + 8)
        {
            bytes = new byte[length * 3 + 8];
            buffer = new UnsafeBufferEx(bytes);
        }

        int position = 0;
        int index = 0;

        if (highSurrogate != 0 && length > 0)
        {
            final char c = chars.charAt(0);

            if (Character.isLowSurrogate(c))
            {
                position = putCodePoint(Character.toCodePoint(highSurrogate, c), position);
                index = 1;
            }
            else
            {
                position = putCodePoint(REPLACEMENT_CHARACTER, position);
            }

            highSurrogate = 0;
        }

        while (index < length)
        {
            final char c = chars.charAt(index++);

            if (Character.isHighSurrogate(c))
            {
                if (index < length && Character.isLowSurrogate(chars.charAt(index)))
                {
                    position = putCodePoint(Character.toCodePoint(c, chars.charAt(index++)), position);
                }
                else if (index < length || last)
                {
                    position = putCodePoint(REPLACEMENT_CHARACTER, position);
                }
                else
                {
                    highSurrogate = c;
                }
            }
            else if (Character.isLowSurrogate(c))
            {
                position = putCodePoint(REPLACEMENT_CHARACTER, position);
            }
            else
            {
                position = putCodePoint(c, position);
            }
        }

        if (last && highSurrogate != 0)
        {
            position = putCodePoint(REPLACEMENT_CHARACTER, position);
            highSurrogate = 0;
        }

        return position;
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
