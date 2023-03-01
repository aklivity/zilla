/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.grpc.internal.config;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GrpcConditionMatcher
{
    private final Matcher method;
    public final Map<String, Matcher> metadataMatch;

    public GrpcConditionMatcher(
        GrpcConditionConfig condition)
    {
        this.method = condition.method != null ? asMatcher(condition.method) : null;
        this.metadataMatch = condition.metadata != null ? asMatcherMap(condition.metadata) : null;
    }

    public boolean matches(
        CharSequence path,
        Function<String, String> metadataByName)
    {
        boolean match = true;

        if (metadataMatch != null)
        {
            for (Map.Entry<String, Matcher> entry : metadataMatch.entrySet())
            {
                String name = entry.getKey();
                Matcher matcher = entry.getValue();
                String value = metadataByName.apply(name);
                match &= value != null && matcher.reset(value).matches();
            }
        }

        return match && matchMethod(path);
    }

    private boolean matchMethod(
        CharSequence path)
    {
        return this.method == null || this.method.reset(path).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard
                .replace(".", "\\.")
                .replace("$", "\\$")
                .replace("+", "[^/]*")
                .replace("#", ".*"))
            .matcher("");
    }

    private static Map<String, Matcher> asMatcherMap(
        Map<String, String> patterns)
    {
        Map<String, Matcher> matchers = new LinkedHashMap<>();
        patterns.forEach((k, v) -> matchers.put(k, asMatcher(k, v)));
        return matchers;
    }

    private static Matcher asMatcher(
        String header,
        String wildcard)
    {
        String pattern = wildcard.replace(".", "\\.").replace("*", ".*");

        if (":path".equals(header) && !pattern.endsWith(".*"))
        {
            pattern = pattern + "(\\?.*)?";
        }

        return Pattern.compile(pattern).matcher("");
    }
}
