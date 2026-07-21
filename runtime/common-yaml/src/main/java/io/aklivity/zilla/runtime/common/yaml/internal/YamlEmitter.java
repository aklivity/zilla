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
package io.aklivity.zilla.runtime.common.yaml.internal;

import java.io.IOException;
import java.io.Writer;

/**
 * Scalar formatting helpers for the streaming YAML generator: a plain scalar is written verbatim, anything
 * else is double-quoted with the YAML escapes. {@link YamlGeneratorStack} drives the structural layout.
 */
public final class YamlEmitter
{
    private YamlEmitter()
    {
    }

    public static void writeInteger(
        Writer writer,
        long value,
        char[] buffer) throws IOException
    {
        int at = buffer.length;
        long magnitude = value > 0 ? -value : value;
        do
        {
            buffer[--at] = (char) ('0' - (int) (magnitude % 10));
            magnitude /= 10;
        }
        while (magnitude != 0);
        if (value < 0)
        {
            buffer[--at] = '-';
        }
        writer.write(buffer, at, buffer.length - at);
    }

    public static void writeScalar(
        Writer writer,
        String value) throws IOException
    {
        if (plain(value))
        {
            writer.write(value);
        }
        else
        {
            writer.write('"');
            for (int i = 0; i < value.length(); i++)
            {
                char c = value.charAt(i);
                char escape = switch (c)
                {
                case '"' -> '"';
                case '\\' -> '\\';
                case '\b' -> 'b';
                case '\f' -> 'f';
                case '\n' -> 'n';
                case '\r' -> 'r';
                case '\t' -> 't';
                default -> '\0';
                };
                if (escape == '\0')
                {
                    writer.write(c);
                }
                else
                {
                    writer.write('\\');
                    writer.write(escape);
                }
            }
            writer.write('"');
        }
    }

    private static boolean plain(
        String value)
    {
        return !value.isEmpty() &&
            !value.isBlank() &&
            isPlainChars(value) &&
            !value.startsWith("-") &&
            !value.startsWith("+") &&
            !value.startsWith(".") &&
            !"true".equalsIgnoreCase(value) &&
            !"false".equalsIgnoreCase(value) &&
            !"null".equalsIgnoreCase(value) &&
            !"~".equals(value) &&
            !looksLikeNumber(value);
    }

    private static boolean isPlainChars(
        String value)
    {
        boolean plain = true;
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            boolean allowed = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' ||
                c == '_' || c == '.' || c == '/' || c == '@' || c == '+' || c == '-';
            if (!allowed)
            {
                plain = false;
                break;
            }
        }
        return plain;
    }

    private static boolean looksLikeNumber(
        String value)
    {
        int length = value.length();
        int at = integerPart(value);
        boolean number = at > 0;
        if (number && at < length && value.charAt(at) == '.')
        {
            int start = at + 1;
            at = digits(value, start);
            number = at > start;
        }
        if (number && at < length && (value.charAt(at) == 'e' || value.charAt(at) == 'E'))
        {
            int start = at + 1;
            if (start < length && (value.charAt(start) == '+' || value.charAt(start) == '-'))
            {
                start++;
            }
            at = digits(value, start);
            number = at > start;
        }
        return number && at == length;
    }

    private static int integerPart(
        String value)
    {
        int at;
        char first = value.isEmpty() ? '\0' : value.charAt(0);
        if (first == '0')
        {
            at = 1;
        }
        else if (first >= '1' && first <= '9')
        {
            at = digits(value, 1);
        }
        else
        {
            at = 0;
        }
        return at;
    }

    private static int digits(
        String value,
        int start)
    {
        int at = start;
        while (at < value.length() && value.charAt(at) >= '0' && value.charAt(at) <= '9')
        {
            at++;
        }
        return at;
    }
}
