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
package io.aklivity.zilla.runtime.common.openapi.view;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.openapi.model.OpenapiResponse;
import io.aklivity.zilla.runtime.common.openapi.model.resolver.OpenapiResolver;

public final class OpenapiResponseView
{
    public final OpenapiOperationView operation;
    public final String status;

    public final Map<String, OpenapiHeaderView> headers;
    public final Map<String, OpenapiMediaTypeView> content;
    public final Map<String, OpenapiLinkView> links;

    public final OpenapiSchemaView schema;

    private final Map<String, Object> extensions;

    OpenapiResponseView(
        OpenapiOperationView operation,
        OpenapiResolver resolver,
        String status,
        OpenapiResponse model)
    {
        this.operation = operation;
        this.status = status;

        this.headers = model.headers != null
                ? model.headers.entrySet().stream()
                    .map(e -> new OpenapiHeaderView(resolver, e.getKey(), e.getValue()))
                    .collect(toMap(c -> c.name, identity()))
                : null;

        this.content = model.content != null
                ? model.content.entrySet().stream()
                    .map(e -> new OpenapiMediaTypeView(resolver, e.getKey(), e.getValue()))
                    .collect(toMap(c -> c.name, identity(), (a, b) -> b, LinkedHashMap::new))
                : null;

        this.links = model.links != null
                ? model.links.entrySet().stream()
                    .map(e -> new OpenapiLinkView(resolver, e.getKey(), e.getValue()))
                    .collect(toMap(c -> c.name, identity()))
                : null;

        this.schema = model.schema != null
                ? new OpenapiSchemaView(resolver, model.schema)
                : null;

        this.extensions = model.extensions;
    }

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }
}
