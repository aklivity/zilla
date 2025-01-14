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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiSchemaView
{
    public final String name;
    public final OpenapiSchema model;

    public final String type;
    public final OpenapiSchemaView items;
    public final Map<String, OpenapiSchemaView> properties;
    public final List<String> required;
    public final String format;
    public final String description;
    public final Integer minimum;
    public final Integer maximum;
    public final List<String> values;
    public final OpenapiSchemaView schema;

    OpenapiSchemaView(
        OpenapiResolver resolver,
        OpenapiSchema model)
    {
        this(resolver.schemas.resolveRef(model.ref), resolver.schemas.resolve(model), resolver);
    }

    private OpenapiSchemaView(
        String name,
        OpenapiSchema resolved,
        OpenapiResolver resolver)
    {
        this.name = name;
        this.model = resolved;

        this.type = resolved.type;
        this.items = resolved.items != null
            ? new OpenapiSchemaView(resolver, resolved.items)
            : null;
        this.properties = resolved.properties != null
            ? resolved.properties.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> new OpenapiSchemaView(resolver, e.getValue())))
            : null;
        this.required = resolved.required;
        this.format = resolved.format;
        this.description = resolved.description;
        this.minimum = resolved.minimum;
        this.maximum = resolved.maximum;
        this.values = resolved.values;
        this.schema = resolved.schema != null
            ? new OpenapiSchemaView(resolver, resolved.schema)
            : null;
    }
}
