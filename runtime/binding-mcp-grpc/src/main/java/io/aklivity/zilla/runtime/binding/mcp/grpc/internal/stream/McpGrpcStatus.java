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

import java.io.ByteArrayOutputStream;
import java.util.Map;

final class McpGrpcStatus
{
    private static final String ABORTED = "10";
    private static final String UNKNOWN = "UNKNOWN";

    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("1", "CANCELLED"),
        Map.entry("2", UNKNOWN),
        Map.entry("3", "INVALID_ARGUMENT"),
        Map.entry("4", "DEADLINE_EXCEEDED"),
        Map.entry("5", "NOT_FOUND"),
        Map.entry("6", "ALREADY_EXISTS"),
        Map.entry("7", "PERMISSION_DENIED"),
        Map.entry("8", "RESOURCE_EXHAUSTED"),
        Map.entry("9", "FAILED_PRECONDITION"),
        Map.entry(ABORTED, "ABORTED"),
        Map.entry("11", "OUT_OF_RANGE"),
        Map.entry("12", "UNIMPLEMENTED"),
        Map.entry("13", "INTERNAL"),
        Map.entry("14", "UNAVAILABLE"),
        Map.entry("15", "DATA_LOSS"),
        Map.entry("16", "UNAUTHENTICATED"));

    static String name(
        String status)
    {
        return status != null ? NAMES.getOrDefault(status, UNKNOWN) : UNKNOWN;
    }

    static boolean transport(
        String status)
    {
        return status == null || ABORTED.equals(status);
    }

    static String describe(
        String status,
        String message)
    {
        final String name = name(status);
        return message != null ? "%s: %s".formatted(name, decode(message)) : name;
    }

    static String decode(
        String message)
    {
        final byte[] bytes = message.getBytes(UTF_8);
        final ByteArrayOutputStream decoded = new ByteArrayOutputStream(bytes.length);

        int index = 0;
        while (index < bytes.length)
        {
            final int high = index + 2 < bytes.length && bytes[index] == '%' ? Character.digit(bytes[index + 1], 16) : -1;
            final int low = high != -1 ? Character.digit(bytes[index + 2], 16) : -1;

            if (high != -1 && low != -1)
            {
                decoded.write(high << 4 | low);
                index += 3;
            }
            else
            {
                decoded.write(bytes[index]);
                index++;
            }
        }

        return decoded.toString(UTF_8);
    }

    private McpGrpcStatus()
    {
    }
}
