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
package io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;

public final class OpenapiSchemaResolver
{
    private final ResolverImpl resolver;

    public OpenapiSchemaResolver(
        Openapi model)
    {
        this.resolver = new ResolverImpl(model);
    }

    public OpenapiSchema resolve(
        OpenapiSchema model)
    {
        OpenapiSchema resolved = resolver.resolve(model);

        if (resolved.schema != null)
        {
            resolved.schema = resolve(resolved.schema);
        }

        if (resolved.items != null)
        {
            resolved.items = resolve(resolved.items);
        }

        if (resolved.properties != null)
        {
            resolved.properties = resolved.properties.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> resolve(entry.getValue())));
        }

        return resolved;
    }

    public String resolveRef(
        String ref)
    {
        return resolver.resolveRef(ref);
    }

    private final class ResolverImpl extends AbstractOpenapiResolver<OpenapiSchema>
    {
        private ResolverImpl(
            Openapi model)
        {
            super(
                Optional.ofNullable(model.components)
                    .map(c -> c.schemas)
                    .orElseGet(Map::of),
                Pattern.compile("#/components/schemas/(.+)"));
        }
    }
}
