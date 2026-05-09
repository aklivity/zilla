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

import static io.aklivity.zilla.runtime.engine.config.WithConfig.NO_COMPOSITE_ID;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

public class HttpWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");

    private final Matcher paramsMatcher;
    private final HttpWithConfig with;

    private Function<MatchResult, String> replacer = r -> null;

    public HttpWithResolver(
        HttpWithConfig with)
    {
        this.paramsMatcher = PARAMS_PATTERN.matcher("");
        this.with = with;
    }

    public void onConditionMatched(
        HttpConditionMatcher condition)
    {
        this.replacer = r -> condition.parameter(r.group(1));
    }

    public long compositeId()
    {
        return with != null ? with.compositeId : NO_COMPOSITE_ID;
    }

    public Map<String8FW, String16FW> resolveOverrides()
    {
        Map<String8FW, String16FW> overrides = new LinkedHashMap<>();
        if (with != null && with.overrides != null)
        {
            with.overrides.forEach((k, v) ->
            {
                String value = v.asString();
                Matcher overrideMatcher = paramsMatcher.reset(value);
                if (overrideMatcher.find())
                {
                    value = overrideMatcher.replaceAll(replacer);
                }
                overrides.put(k, new String16FW(value));
            });
        }
        return overrides;
    }
}
