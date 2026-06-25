/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json.internal;

import jakarta.json.stream.JsonParsingException;

import org.agrona.DirectBuffer;

// Decodes the raw body of a JSON string (the bytes between the surrounding quotes, escapes and
// UTF-8 intact) into a StringBuilder, applying JSON unescaping. The tokenizer validates the body
// during the scan, so this slice-based decode assumes well-formed input and exists to materialize a
// value lazily on demand without retaining decoded chars during the scan.
final class JsonStrings
{
    private JsonStrings()
    {
    }

    static void unescape(
        DirectBuffer buffer,
        int offset,
        int length,
        StringBuilder out)
    {
        final int limit = offset + length;
        int index = offset;
        while (index < limit)
        {
            final int c = buffer.getByte(index) & 0xff;
            index++;
            if (c == '\\')
            {
                index = unescapeEscape(buffer, index, out);
            }
            else if (c < 0x80)
            {
                out.append((char) c);
            }
            else
            {
                index = appendUtf8(buffer, index, c, out);
            }
        }
    }

    private static int unescapeEscape(
        DirectBuffer buffer,
        int index,
        StringBuilder out)
    {
        int progress = index;
        final int e = buffer.getByte(progress) & 0xff;
        progress++;
        switch (e)
        {
        case '"':
            out.append('"');
            break;
        case '\\':
            out.append('\\');
            break;
        case '/':
            out.append('/');
            break;
        case 'b':
            out.append('\b');
            break;
        case 'f':
            out.append('\f');
            break;
        case 'n':
            out.append('\n');
            break;
        case 'r':
            out.append('\r');
            break;
        case 't':
            out.append('\t');
            break;
        case 'u':
            int value = 0;
            for (int k = 0; k < 4; k++)
            {
                value = (value << 4) | hexDigit(buffer.getByte(progress) & 0xff);
                progress++;
            }
            out.append((char) value);
            break;
        default:
            throw new JsonParsingException("Invalid escape: \\" + (char) e, null);
        }
        return progress;
    }

    private static int appendUtf8(
        DirectBuffer buffer,
        int index,
        int first,
        StringBuilder out)
    {
        int remaining;
        int code;
        if ((first & 0xe0) == 0xc0)
        {
            code = first & 0x1f;
            remaining = 1;
        }
        else if ((first & 0xf0) == 0xe0)
        {
            code = first & 0x0f;
            remaining = 2;
        }
        else if ((first & 0xf8) == 0xf0)
        {
            code = first & 0x07;
            remaining = 3;
        }
        else
        {
            throw new JsonParsingException("Invalid UTF-8 lead byte: " + first, null);
        }
        int progress = index;
        for (int k = 0; k < remaining; k++)
        {
            final int cont = buffer.getByte(progress) & 0xff;
            progress++;
            code = (code << 6) | (cont & 0x3f);
        }
        out.appendCodePoint(code);
        return progress;
    }

    private static int hexDigit(
        int c)
    {
        final int value;
        if (c >= '0' && c <= '9')
        {
            value = c - '0';
        }
        else if (c >= 'a' && c <= 'f')
        {
            value = c - 'a' + 10;
        }
        else if (c >= 'A' && c <= 'F')
        {
            value = c - 'A' + 10;
        }
        else
        {
            throw new JsonParsingException("Invalid hex digit: " + c, null);
        }
        return value;
    }
}
