/*
 * Copyright 2021-2024 Aklivity Inc.
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

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.config.HttpAffinityConfig;

public abstract class HttpAffinityResolver
{
    public static final HttpAffinityResolver NONE = new HttpAffinityResolver()
    {
        @Override
        public String resolveKey(
            Function<String, String> headerByName)
        {
            return null;
        }
    };

    private static final String HEADER_PATH = ":path";

    public static HttpAffinityResolver of(
        HttpAffinityConfig affinity)
    {
        if (affinity == null)
        {
            return NONE;
        }

        final Matcher matcher = affinity.match != null ? affinity.match.matcher("") : null;

        return switch (affinity.source)
        {
        case HEADER -> new HeaderAffinityResolver(affinity.name, matcher);
        case QUERY -> new QueryAffinityResolver(affinity.name, matcher);
        };
    }

    public abstract String resolveKey(
        Function<String, String> headerByName);

    private static String applyMatcher(
        String value,
        Matcher matcher)
    {
        String key = value;

        if (value != null &&
            matcher != null)
        {
            key = matcher.reset(value).find()
                ? matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0)
                : null;
        }

        return key;
    }

    private static final class HeaderAffinityResolver extends HttpAffinityResolver
    {
        private final String name;
        private final Matcher matcher;

        private HeaderAffinityResolver(
            String name,
            Matcher matcher)
        {
            this.name = name;
            this.matcher = matcher;
        }

        @Override
        public String resolveKey(
            Function<String, String> headerByName)
        {
            return applyMatcher(headerByName.apply(name), matcher);
        }
    }

    private static final class QueryAffinityResolver extends HttpAffinityResolver
    {
        private final Matcher queryMatcher;
        private final Matcher matcher;

        private QueryAffinityResolver(
            String name,
            Matcher matcher)
        {
            // matches `?<name>=<value>` or `&<name>=<value>` in :path; group 1 is the value
            this.queryMatcher = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&]*)").matcher("");
            this.matcher = matcher;
        }

        @Override
        public String resolveKey(
            Function<String, String> headerByName)
        {
            final String path = headerByName.apply(HEADER_PATH);
            String value = null;

            if (path != null && queryMatcher.reset(path).find())
            {
                value = queryMatcher.group(1);
            }

            return applyMatcher(value, matcher);
        }
    }
}
