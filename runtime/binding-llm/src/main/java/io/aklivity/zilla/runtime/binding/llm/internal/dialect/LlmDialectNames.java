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

public final class LlmDialectNames
{
    private static final char VARIANT = '/';

    public static String vendor(
        String dialect)
    {
        final int variant = dialect.indexOf(VARIANT);

        return variant < 0 ? dialect : dialect.substring(0, variant);
    }

    public static boolean admits(
        String pattern,
        String dialect)
    {
        return pattern.equals(dialect) ||
            pattern.indexOf(VARIANT) < 0 &&
            dialect != null &&
            dialect.length() > pattern.length() &&
            dialect.charAt(pattern.length()) == VARIANT &&
            dialect.startsWith(pattern);
    }

    private LlmDialectNames()
    {
    }
}
