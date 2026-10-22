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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.config;

import java.util.regex.Pattern;

final class McpGrpcNames
{
    static final int MAX_TOOL_NAME_LENGTH = 64;

    private static final Pattern TOOL_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,%d}$".formatted(MAX_TOOL_NAME_LENGTH));
    private static final char SEPARATOR = '_';

    static String toolName(
        String service,
        String method)
    {
        final int packageEnd = service.lastIndexOf('.');
        final String packageName = packageEnd != -1 ? service.substring(0, packageEnd) : "";
        final String serviceName = packageEnd != -1 ? service.substring(packageEnd + 1) : service;

        final StringBuilder name = new StringBuilder();
        if (!packageName.isEmpty())
        {
            name.append(snake(packageName)).append(SEPARATOR);
        }
        name.append(snake(serviceName)).append(SEPARATOR).append(snake(method));

        return name.toString();
    }

    static boolean validToolName(
        String name)
    {
        return TOOL_NAME.matcher(name).matches();
    }

    static String snake(
        String value)
    {
        final StringBuilder result = new StringBuilder();

        for (int index = 0; index < value.length(); index++)
        {
            final char current = value.charAt(index);

            if (Character.isUpperCase(current))
            {
                if (wordBoundary(value, index))
                {
                    separate(result);
                }
                result.append(Character.toLowerCase(current));
            }
            else if (Character.isLetterOrDigit(current))
            {
                result.append(current);
            }
            else
            {
                separate(result);
            }
        }

        final int length = result.length();
        if (length > 0 && result.charAt(length - 1) == SEPARATOR)
        {
            result.setLength(length - 1);
        }

        return result.toString();
    }

    private static boolean wordBoundary(
        String value,
        int index)
    {
        final boolean afterLowerOrDigit = index > 0 &&
            (Character.isLowerCase(value.charAt(index - 1)) || Character.isDigit(value.charAt(index - 1)));
        final boolean endsAcronym = index > 0 && index + 1 < value.length() &&
            Character.isUpperCase(value.charAt(index - 1)) && Character.isLowerCase(value.charAt(index + 1));

        return afterLowerOrDigit || endsAcronym;
    }

    private static void separate(
        StringBuilder result)
    {
        final int length = result.length();
        if (length > 0 && result.charAt(length - 1) != SEPARATOR)
        {
            result.append(SEPARATOR);
        }
    }

    private McpGrpcNames()
    {
    }
}
