/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.internal.util;

import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class Mustache
{
    private static final Pattern MUSTACHE_PATTERN = Pattern.compile("\\{\\{\\s*env\\.([^\\s\\}]*)\\s*\\}\\}");

    public static String resolve(
        String template,
        UnaryOperator<String> env)
    {
        return MUSTACHE_PATTERN
                .matcher(template)
                .replaceAll(r -> Optional.ofNullable(env.apply(r.group(1))).orElse(""));
    }

    private Mustache()
    {
    }
}
