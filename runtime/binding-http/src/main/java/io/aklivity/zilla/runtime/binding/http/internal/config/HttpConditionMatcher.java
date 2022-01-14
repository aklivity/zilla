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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpConditionMatcher
{
    private final Map<String, Matcher> headersMatch;

    public HttpConditionMatcher(
        HttpConditionConfig condition)
    {
        this.headersMatch = condition.headers != null ? asMatcherMap(condition.headers) : null;
    }

    public boolean matches(
        Function<String, String> headerByName)
    {
        boolean match = true;

        if (headersMatch != null)
        {
            for (Map.Entry<String, Matcher> entry : headersMatch.entrySet())
            {
                String name = entry.getKey();
                Matcher matcher = entry.getValue();
                String value = headerByName.apply(name);
                match &= value != null && matcher.reset(value).matches();
            }
        }

        return match;
    }

    private static Map<String, Matcher> asMatcherMap(
        Map<String, String> patterns)
    {
        Map<String, Matcher> matchers = new LinkedHashMap<>();
        patterns.forEach((k, v) -> matchers.put(k, asMatcher(v)));
        return matchers;
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }
}
