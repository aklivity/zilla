/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AbstractAsyncapiResolvable;

public abstract class AbstractAsyncapiResolver<T extends AbstractAsyncapiResolvable>
{
    private final Map<String, T> resolvables;
    private final Matcher matcher;
    private final Set<String> unresolved;

    public AbstractAsyncapiResolver(
        Map<String, T> resolvables,
        Pattern pattern,
        Set<String> unresolved)
    {
        this.resolvables = resolvables;
        this.matcher = pattern.matcher("");
        this.unresolved = unresolved;
    }

    public final T resolve(
        T model)
    {
        final String key = resolveRef(model.ref);
        T candidate = key != null ? resolvables.getOrDefault(key, model) : model;
        if (candidate.ref != null)
        {
            unresolved.add(candidate.ref);
        }
        return candidate;
    }

    public T resolve(
        String key)
    {
        return resolvables.get(key);
    }

    public final String resolveRef(
        String ref)
    {
        return ref != null && matcher.reset(ref).matches() ? matcher.group(1) : null;
    }
}
