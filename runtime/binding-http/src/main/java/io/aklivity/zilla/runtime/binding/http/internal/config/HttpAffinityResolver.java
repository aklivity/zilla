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
import io.aklivity.zilla.runtime.binding.http.config.HttpAffinitySource;

public final class HttpAffinityResolver
{
    private final HttpAffinitySource source;
    private final String name;
    private final Matcher matcher;
    private final Pattern queryPattern;
    private final Matcher queryMatcher;

    public HttpAffinityResolver(
        HttpAffinityConfig affinity)
    {
        this.source = affinity.source;
        this.name = affinity.name;
        this.matcher = affinity.match != null ? affinity.match.matcher("") : null;
        if (source == HttpAffinitySource.QUERY)
        {
            // matches `?<name>=<value>` or `&<name>=<value>` in :path; group 1 is the value
            this.queryPattern = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&]*)");
            this.queryMatcher = queryPattern.matcher("");
        }
        else
        {
            this.queryPattern = null;
            this.queryMatcher = null;
        }
    }

    public String resolveKey(
        Function<String, String> headerByName,
        String path)
    {
        String value = null;

        switch (source)
        {
        case HEADER:
            value = headerByName.apply(name);
            break;
        case QUERY:
            if (path != null)
            {
                queryMatcher.reset(path);
                if (queryMatcher.find())
                {
                    value = queryMatcher.group(1);
                }
            }
            break;
        }

        if (value == null)
        {
            return null;
        }

        if (matcher == null)
        {
            return value;
        }

        matcher.reset(value);
        if (!matcher.find())
        {
            return null;
        }

        return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0);
    }
}
