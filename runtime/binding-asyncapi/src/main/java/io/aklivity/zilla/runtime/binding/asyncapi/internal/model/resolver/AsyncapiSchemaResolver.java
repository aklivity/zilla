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
import java.util.Optional;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchemaItem;

public final class AsyncapiSchemaResolver
{
    private final ResolverImpl resolver;

    public AsyncapiSchemaResolver(
        Asyncapi model)
    {
        this.resolver = new ResolverImpl(model);
    }

    @SuppressWarnings("unchecked")
    public <T extends AsyncapiSchemaItem> T resolve(
        T model)
    {
        return (T) resolver.resolve(model);
    }

    public AsyncapiSchema resolve(
        AsyncapiSchema model)
    {
        final AsyncapiSchema resolved = (AsyncapiSchema) resolver.resolve(model);

        if (resolved.schema != null)
        {
            resolved.schema = resolve(resolved.schema);
        }

        if (resolved.items != null)
        {
            resolved.items = resolve(resolved.items);
        }

        return resolved;
    }

    public String resolveRef(
        String ref)
    {
        return resolver.resolveRef(ref);
    }

    private final class ResolverImpl extends AbstractAsyncapiResolver<AsyncapiSchemaItem>
    {
        private ResolverImpl(
            Asyncapi model)
        {
            super(
                Optional.ofNullable(model.components)
                    .map(c -> c.schemas)
                    .orElseGet(Map::of),
                Pattern.compile("#/components/schemas/(.+)"));
        }
    }
}
