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
package io.aklivity.zilla.runtime.binding.llm.internal.dialect;

import java.util.Map;

public final class LlmDialectNames
{
    private static final char VARIANT = '/';
    private static final Map<String, String> DEFAULTS = Map.of("openai", "openai/ccs");
    private static final Map<String, String> APIS = Map.of(
        "openai/ccs", "chat/completions",
        "openai/rs", "responses");

    public static String vendor(
        String dialect)
    {
        final int variant = dialect.indexOf(VARIANT);

        return variant < 0 ? dialect : dialect.substring(0, variant);
    }

    public static String defaultOf(
        String dialect)
    {
        return dialect != null ? DEFAULTS.getOrDefault(dialect, dialect) : null;
    }

    public static String api(
        String dialect)
    {
        return dialect != null ? APIS.get(dialect) : null;
    }

    public static String id(
        String vendor,
        String api)
    {
        String id = null;

        for (Map.Entry<String, String> entry : APIS.entrySet())
        {
            if (id == null && entry.getValue().equals(api) && vendor(entry.getKey()).equals(vendor))
            {
                id = entry.getKey();
            }
        }

        return id;
    }

    public static boolean admits(
        String pattern,
        String dialect)
    {
        return pattern != null && admits(pattern, 0, pattern.length(), dialect);
    }

    public static boolean admits(
        CharSequence pattern,
        int from,
        int to,
        String dialect)
    {
        boolean admitted = false;

        if (pattern != null && dialect != null)
        {
            final int length = to - from;
            final boolean named = dialect.length() == length;
            final boolean vendor = dialect.length() > length && dialect.charAt(length) == VARIANT;

            admitted = (named || vendor && !variant(pattern, from, to)) && prefixes(pattern, from, length, dialect);
        }

        return admitted;
    }

    private static boolean variant(
        CharSequence pattern,
        int from,
        int to)
    {
        boolean found = false;

        for (int index = from; !found && index < to; index++)
        {
            found = pattern.charAt(index) == VARIANT;
        }

        return found;
    }

    private static boolean prefixes(
        CharSequence pattern,
        int from,
        int length,
        String dialect)
    {
        boolean matched = true;

        for (int index = 0; matched && index < length; index++)
        {
            matched = pattern.charAt(from + index) == dialect.charAt(index);
        }

        return matched;
    }

    private LlmDialectNames()
    {
    }
}
