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

import java.util.Arrays;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

// Decodes the content of a JSON string value that carries media into the bytes of the media, as they arrive. A data URI
// is decoded when it declares base64, a reference to media held elsewhere is not decoded, and a value without a data URI
// scheme is decoded as base64 on its own.
final class LlmMediaDecoder
{
    private static final DirectBufferEx TEXT_OPEN = new UnsafeBufferEx("{\"text\":\"".getBytes(UTF_8));
    private static final DirectBufferEx TEXT_CLOSE = new UnsafeBufferEx("\"}".getBytes(UTF_8));
    private static final byte[] SCHEME = "data:".getBytes(UTF_8);
    private static final byte[] BASE64 = ";base64".getBytes(UTF_8);
    private static final int HEADER_LIMIT = 256;
    private static final int INITIAL_CAPACITY = 4096;

    @FunctionalInterface
    private interface MediaDecoder
    {
        void decode(
            byte value);
    }

    private final boolean dataUri;
    private final LlmTextPipeline unescaper;
    private final byte[] header;
    private final MediaDecoder decodeScheme;
    private final MediaDecoder decodeHeader;
    private final MediaDecoder decodeBase64;
    private final MediaDecoder decodePadding;
    private final MediaDecoder decodeIgnore;
    private final MediaDecoder decodeInvalid;

    private MediaDecoder decoder;
    private byte[] decoded;
    private int length;
    private int matched;
    private int headerLength;
    private int accumulated;
    private int sextets;

    LlmMediaDecoder(
        boolean dataUri)
    {
        this.dataUri = dataUri;
        this.unescaper = new LlmTextPipeline(this::onText);
        this.header = new byte[HEADER_LIMIT];
        this.decodeScheme = this::decodeScheme;
        this.decodeHeader = this::decodeHeader;
        this.decodeBase64 = this::decodeBase64;
        this.decodePadding = this::decodePadding;
        this.decodeIgnore = value ->
        {
        };
        this.decodeInvalid = value ->
        {
        };
        this.decoded = new byte[INITIAL_CAPACITY];
        begin();
    }

    void begin()
    {
        unescaper.begin();
        unescaper.feed(TEXT_OPEN, 0, TEXT_OPEN.capacity(), false);

        decoder = dataUri ? decodeScheme : decodeBase64;
        length = 0;
        matched = 0;
        headerLength = 0;
        accumulated = 0;
        sextets = 0;
    }

    boolean feed(
        DirectBufferEx buffer,
        int offset,
        int length,
        boolean last)
    {
        boolean valid = unescaper.feed(buffer, offset, length, false);

        if (last)
        {
            valid &= unescaper.feed(TEXT_CLOSE, 0, TEXT_CLOSE.capacity(), true);

            if (decoder == decodeBase64)
            {
                flush();
            }
        }

        return valid && decoder != decodeInvalid;
    }

    byte[] decoded()
    {
        return decoded;
    }

    int length()
    {
        return length;
    }

    void drain()
    {
        length = 0;
    }

    private void onText(
        DirectBufferEx buffer,
        int offset,
        int length)
    {
        for (int index = 0; index < length; index++)
        {
            decoder.decode(buffer.getByte(offset + index));
        }
    }

    private void decodeScheme(
        byte value)
    {
        if (value == SCHEME[matched])
        {
            matched++;
            decoder = matched == SCHEME.length ? decodeHeader : decoder;
        }
        else
        {
            decoder = decodeIgnore;
        }
    }

    private void decodeHeader(
        byte value)
    {
        if (value == ',')
        {
            decoder = endsWithBase64() ? decodeBase64 : decodeIgnore;
        }
        else if (headerLength < HEADER_LIMIT)
        {
            header[headerLength++] = value;
        }
        else
        {
            decoder = decodeIgnore;
        }
    }

    private void decodeBase64(
        byte value)
    {
        final int sextet = sextet(value);

        if (sextet >= 0)
        {
            accumulated = accumulated << 6 | sextet;
            sextets++;

            if (sextets == 4)
            {
                emit(accumulated >> 16);
                emit(accumulated >> 8);
                emit(accumulated);
                accumulated = 0;
                sextets = 0;
            }
        }
        else if (value == '=')
        {
            flush();
            decoder = decoder == decodeInvalid ? decodeInvalid : decodePadding;
        }
        else if (!isWhitespace(value))
        {
            decoder = decodeInvalid;
        }
    }

    private void decodePadding(
        byte value)
    {
        if (value != '=' && !isWhitespace(value))
        {
            decoder = decodeInvalid;
        }
    }

    private void flush()
    {
        if (sextets == 1)
        {
            decoder = decodeInvalid;
        }
        else if (sextets == 2)
        {
            emit(accumulated >> 4);
        }
        else if (sextets == 3)
        {
            emit(accumulated >> 10);
            emit(accumulated >> 2);
        }

        accumulated = 0;
        sextets = 0;
    }

    private void emit(
        int value)
    {
        if (length == decoded.length)
        {
            decoded = Arrays.copyOf(decoded, length << 1);
        }

        decoded[length++] = (byte) value;
    }

    private boolean endsWithBase64()
    {
        boolean ends = headerLength >= BASE64.length;

        for (int index = 0; ends && index < BASE64.length; index++)
        {
            ends = header[headerLength - BASE64.length + index] == BASE64[index];
        }

        return ends;
    }

    private static int sextet(
        byte value)
    {
        final int result;

        if (value >= 'A' && value <= 'Z')
        {
            result = value - 'A';
        }
        else if (value >= 'a' && value <= 'z')
        {
            result = value - 'a' + 26;
        }
        else if (value >= '0' && value <= '9')
        {
            result = value - '0' + 52;
        }
        else if (value == '+' || value == '-')
        {
            result = 62;
        }
        else if (value == '/' || value == '_')
        {
            result = 63;
        }
        else
        {
            result = -1;
        }

        return result;
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }
}
