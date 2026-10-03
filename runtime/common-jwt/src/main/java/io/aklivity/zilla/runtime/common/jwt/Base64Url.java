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

import java.util.Base64;

final class Base64Url
{
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    static byte[] decode(
        String value) throws JwtException
    {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '=')
        {
            end--;
        }

        StringBuilder normalized = new StringBuilder(end);
        for (int i = 0; i < end; i++)
        {
            char c = value.charAt(i);

            if (c == '+')
            {
                normalized.append('-');
            }
            else if (c == '/')
            {
                normalized.append('_');
            }
            else if (!isWhitespace(c))
            {
                normalized.append(c);
            }
        }

        byte[] decoded;
        try
        {
            decoded = DECODER.decode(normalized.toString());
        }
        catch (IllegalArgumentException ex)
        {
            throw new JwtException("Invalid base64url value", ex);
        }

        return decoded;
    }

    static String encode(
        byte[] bytes)
    {
        return ENCODER.encodeToString(bytes);
    }

    private static boolean isWhitespace(
        char c)
    {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private Base64Url()
    {
    }
}
