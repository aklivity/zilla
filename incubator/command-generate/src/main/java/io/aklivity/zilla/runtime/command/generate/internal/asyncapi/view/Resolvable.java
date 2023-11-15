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
package io.aklivity.zilla.runtime.command.generate.internal.asyncapi.view;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Resolvable<T>
{
    private final Map<String, T> map;
    private final Matcher matcher;

    protected String key;

    public Resolvable(
        Map<String, T> map,
        String regex)
    {
        this.map = map;
        this.matcher = Pattern.compile(regex).matcher("");
    }

    protected T resolveRef(
        String ref)
    {
        T result = null;
        matcher.reset(ref);
        if (matcher.matches())
        {
            key = matcher.group(1);
            result = map.get(key);
        }
        return result;
    }
}
