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
package io.aklivity.zilla.runtime.binding.agui.internal.config;

import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import io.aklivity.zilla.config.binding.agui.AguiWithConfig;

public final class AguiWithResolver
{
    private static final Pattern PARAMS_PATTERN = Pattern.compile("\\$\\{params\\.([a-zA-Z_]+)\\}");

    private final String agent;

    private Function<MatchResult, String> replacer = r -> "";

    public AguiWithResolver(
        AguiWithConfig with)
    {
        this.agent = with.agent;
    }

    public void onConditionMatched(
        AguiConditionMatcher condition)
    {
        this.replacer = r -> condition.parameter(r.group(1));
    }

    public String resolveAgent()
    {
        return agent != null
            ? PARAMS_PATTERN.matcher(agent).replaceAll(replacer)
            : null;
    }
}
