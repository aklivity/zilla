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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class McpGrpcJson
{
    private static final int MAX_ESCAPED_PER_BYTE = 6;
    private static final byte[] HEX = "0123456789abcdef".getBytes(UTF_8);

    static int maxEscaped(
        int length)
    {
        return length * MAX_ESCAPED_PER_BYTE;
    }

    static int putEscaped(
        MutableDirectBufferEx dst,
        int offset,
        String text)
    {
        final byte[] bytes = text.getBytes(UTF_8);
        return putEscaped(dst, offset, new UnsafeBufferEx(bytes), 0, bytes.length);
    }

    static int putEscaped(
        MutableDirectBufferEx dst,
        int offset,
        DirectBufferEx src,
        int srcOffset,
        int length)
    {
        int progress = offset;

        for (int index = srcOffset; index < srcOffset + length; index++)
        {
            final byte value = src.getByte(index);

            switch (value)
            {
            case '"':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) '"');
                break;
            case '\\':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) '\\');
                break;
            case '\n':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) 'n');
                break;
            case '\r':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) 'r');
                break;
            case '\t':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) 't');
                break;
            case '\b':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) 'b');
                break;
            case '\f':
                dst.putByte(progress++, (byte) '\\');
                dst.putByte(progress++, (byte) 'f');
                break;
            default:
                if (value >= 0 && value < 0x20)
                {
                    dst.putByte(progress++, (byte) '\\');
                    dst.putByte(progress++, (byte) 'u');
                    dst.putByte(progress++, (byte) '0');
                    dst.putByte(progress++, (byte) '0');
                    dst.putByte(progress++, HEX[(value >> 4) & 0xf]);
                    dst.putByte(progress++, HEX[value & 0xf]);
                }
                else
                {
                    dst.putByte(progress++, value);
                }
                break;
            }
        }

        return progress;
    }

    private McpGrpcJson()
    {
    }
}
