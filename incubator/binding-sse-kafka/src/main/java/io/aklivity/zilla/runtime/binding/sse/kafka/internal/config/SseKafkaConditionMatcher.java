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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SseKafkaConditionMatcher
{
    private final Matcher path;

    public SseKafkaConditionMatcher(
        SseKafkaConditionConfig condition)
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
        // TODO: named groups for parameters
        return Pattern.compile(wildcard.replace(".", "\\.").replace("*", ".*")).matcher("");
    }
}
