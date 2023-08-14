/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.sse.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.sse.config.SseConditionConfig;

public final class SseConditionMatcher
{
    private final Matcher path;

    public SseConditionMatcher(
        SseConditionConfig condition)
    {
        this.path = condition.path != null ? asMatcher(condition.path) : null;
    }

    public boolean matches(
        String path)
    {
        return matchPath(path);
    }

    private boolean matchPath(
        String path)
    {
        return this.path == null || this.path.reset(path).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }
}
