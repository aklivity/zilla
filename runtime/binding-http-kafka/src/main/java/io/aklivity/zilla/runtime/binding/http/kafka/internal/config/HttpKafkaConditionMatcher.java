/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpKafkaConditionMatcher
{
    private final Matcher method;
    private final Matcher path;
    private Consumer<HttpKafkaConditionMatcher> observer;

    public HttpKafkaConditionMatcher(
        HttpKafkaConditionConfig condition)
    {
        this.method = condition.method != null ? asMatcher(condition.method) : null;
        this.path = condition.path != null ? asMatcher(condition.path) : null;
    }

    public boolean matches(
        CharSequence method,
        CharSequence path)
    {
        return matchMethod(method) &&
               matchPath(path) &&
               observeMatched();
    }

    public String parameter(
        String name)
    {
        return path.group(name);
    }

    public void observe(
        Consumer<HttpKafkaConditionMatcher> observer)
    {
        this.observer = observer;
    }

    private boolean observeMatched()
    {
        if (observer != null)
        {
            observer.accept(this);
        }

        return true;
    }

    private boolean matchMethod(
        CharSequence method)
    {
        return this.method == null || this.method.reset(method).matches();
    }

    private boolean matchPath(
        CharSequence path)
    {
        return this.path == null || this.path.reset(path).matches();
    }

    private static Matcher asMatcher(
        String wildcard)
    {
        return Pattern.compile(
                wildcard.replace(".", "\\.")
                        .replace("*", ".*")
                        .replaceAll("\\{([a-zA-Z_]+)\\}", "(?<$1>.+)"))
                .matcher("");
    }
}
