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
package io.aklivity.zilla.runtime.common.jwt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class Base64UrlTest
{
    private static final byte[] SYMBOLS = {(byte) 0xfb, (byte) 0xff, (byte) 0xbf};

    @Test
    void shouldDecodeUrlSafeAlphabet() throws Exception
    {
        assertArrayEquals(SYMBOLS, Base64Url.decode("-_-_"));
    }

    @Test
    void shouldDecodeStandardAlphabet() throws Exception
    {
        assertArrayEquals(SYMBOLS, Base64Url.decode("+/+/"));
    }

    @Test
    void shouldDecodeWithOrWithoutPadding() throws Exception
    {
        byte[] expected = "a".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(expected, Base64Url.decode("YQ"));
        assertArrayEquals(expected, Base64Url.decode("YQ=="));
    }

    @Test
    void shouldIgnoreWhitespace() throws Exception
    {
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), Base64Url.decode("YW\r\n Jj\t"));
    }

    @Test
    void shouldRejectInvalidCharacters()
    {
        assertThrows(JwtException.class, () -> Base64Url.decode("Y*Jj"));
    }

    @Test
    void shouldEncodeWithoutPadding()
    {
        assertEquals("YQ", Base64Url.encode("a".getBytes(StandardCharsets.UTF_8)));
        assertEquals("-_-_", Base64Url.encode(SYMBOLS));
    }
}
