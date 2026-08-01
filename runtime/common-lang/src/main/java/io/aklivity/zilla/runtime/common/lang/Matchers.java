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
package io.aklivity.zilla.runtime.common.lang;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Glob-pattern compilation and allow-list matching shared by route condition matchers that
 * accept a {@code *}-wildcard glob (e.g. an MCP binding's {@code tool}/{@code prompt}/
 * {@code resource} condition), so the split-quote-join compile algorithm and null-means-
 * unrestricted allow-list semantics live in one place instead of being copied per binding.
 */
public final class Matchers
{
    private Matchers()
    {
    }

    /**
     * Compiles a single {@code *}-wildcard glob into a whole-string-matching {@link Pattern},
     * quoting literal segments so regex metacharacters other than {@code *} are matched literally.
     */
    public static Pattern glob(
        String glob)
    {
        final StringBuilder regex = new StringBuilder();
        final String[] literals = glob.split("\\*", -1);
        for (int index = 0; index < literals.length; index++)
        {
            if (index > 0)
            {
                regex.append(".*");
            }
            if (!literals[index].isEmpty())
            {
                regex.append(Pattern.quote(literals[index]));
            }
        }
        return Pattern.compile(regex.toString());
    }

    /**
     * Compiles each glob in {@code globs}, preserving {@code null} (meaning "unrestricted")
     * rather than compiling an empty list.
     */
    public static List<Pattern> globAll(
        List<String> globs)
    {
        return globs == null
            ? null
            : globs.stream()
                .map(Matchers::glob)
                .collect(Collectors.toList());
    }

    /**
     * Whether {@code name} is admitted by {@code allow}: {@code true} when {@code allow} is
     * {@code null} (unrestricted), otherwise {@code true} when any pattern in {@code allow}
     * matches {@code name}.
     */
    public static boolean admits(
        List<Pattern> allow,
        String name)
    {
        boolean result = allow == null;

        if (!result)
        {
            for (Pattern pattern : allow)
            {
                if (pattern.matcher(name).matches())
                {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }
}
