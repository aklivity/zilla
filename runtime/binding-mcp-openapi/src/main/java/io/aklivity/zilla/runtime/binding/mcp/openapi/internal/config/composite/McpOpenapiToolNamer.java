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
package io.aklivity.zilla.runtime.binding.mcp.openapi.internal.config.composite;

import java.util.Set;

import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;

final class McpOpenapiToolNamer
{
    private static final String FALLBACK_BASE_NAME = "operation";

    private McpOpenapiToolNamer()
    {
    }

    static String defaultName(
        OpenapiOperationView operation,
        Set<String> used)
    {
        String candidate = operation.id != null ? toSnakeCase(operation.id) : "";

        if (candidate.isEmpty() || used.contains(candidate))
        {
            final String slug = methodPathSlug(operation);
            if (!slug.isEmpty() && (candidate.isEmpty() || !used.contains(slug)))
            {
                candidate = slug;
            }
        }

        if (candidate.isEmpty())
        {
            candidate = FALLBACK_BASE_NAME;
        }

        String name = candidate;
        int suffix = 2;
        while (!used.add(name))
        {
            name = "%s_%d".formatted(candidate, suffix++);
        }

        return name;
    }

    private static String methodPathSlug(
        OpenapiOperationView operation)
    {
        return toSnakeCase("%s_%s".formatted(operation.method, operation.path));
    }

    static String toSnakeCase(
        String value)
    {
        final StringBuilder result = new StringBuilder();
        char previous = '\0';

        for (int i = 0; i < value.length(); i++)
        {
            final char current = value.charAt(i);
            if (Character.isUpperCase(current))
            {
                if (result.length() > 0 &&
                    previous != '_' &&
                    (Character.isLowerCase(previous) || Character.isDigit(previous)))
                {
                    result.append('_');
                }
                result.append(Character.toLowerCase(current));
            }
            else if (Character.isLetterOrDigit(current))
            {
                result.append(current);
            }
            else if (result.length() > 0 && result.charAt(result.length() - 1) != '_')
            {
                result.append('_');
            }
            previous = current;
        }

        int start = 0;
        while (start < result.length() && result.charAt(start) == '_')
        {
            start++;
        }

        int end = result.length();
        while (end > start && result.charAt(end - 1) == '_')
        {
            end--;
        }

        return result.substring(start, end);
    }
}
