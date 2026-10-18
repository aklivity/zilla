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
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;

final class LlmTextSink implements JsonSink
{
    private static final char REPLACEMENT_CHARACTER = '�';

    private final LlmTextPipeline.Output output;

    private byte[] bytes;
    private UnsafeBufferEx buffer;
    private char highSurrogate;

    LlmTextSink(
        LlmTextPipeline.Output output)
    {
        this.output = output;
        this.bytes = new byte[1024];
        this.buffer = new UnsafeBufferEx(bytes);
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event)
    {
        Status status = Status.ADVANCED;

        if (event == JsonEvent.VALUE_STRING)
        {
            final boolean deferred = source.deferredBytes();
            final CharSequence chars = source.getStringView();

            final int encoded = encode(chars, !deferred);

            if (encoded > 0)
            {
                output.put(buffer, 0, encoded);
            }

            control.consumed(chars.length());

            status = deferred ? Status.ADVANCED : Status.COMPLETED;
        }

        return status;
    }

    @Override
    public void reset()
    {
        highSurrogate = 0;
    }

    @Override
    public boolean identity()
    {
        return false;
    }

    @Override
    public boolean deterministic()
    {
        return true;
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
                if (index < length)
                {
                    final char d = chars.charAt(index);
                    if (Character.isLowSurrogate(d))
                    {
                        position = putCodePoint(Character.toCodePoint(c, d), position);
                        index++;
                    }
                    else
                    {
                        position = putCodePoint(REPLACEMENT_CHARACTER, position);
                    }
                }
                else if (last)
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
