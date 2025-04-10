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
import java.util.stream.Collectors;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiSchemaView
{
    public final String name;
    public final OpenapiJsonSchema model;

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
    public List<OpenapiSchemaView> oneOf;

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
        this.model = OpenapiJsonSchema.of(resolved);

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
        this.oneOf = resolved.oneOf != null
            ? resolved.oneOf.stream()
                .map(schema -> new OpenapiSchemaView(resolver, schema))
                .collect(Collectors.toList())
            : null;
    }

    @JsonbPropertyOrder({
        "type",
        "items",
        "properties",
        "required",
        "format",
        "description",
        "enum",
        "title",
        "multipleOf",
        "maximum",
        "exclusiveMaximum",
        "minimum",
        "exclusiveMinimum",
        "maxLength",
        "minLength",
        "pattern",
        "maxItems",
        "minItems",
        "uniqueItems",
        "maxProperties",
        "minProperties",
        "schema",
        "oneOf"
    })
    public static final class OpenapiJsonSchema
    {
        public String type;
        public OpenapiSchema items;
        public Map<String, OpenapiJsonSchema> properties;
        public List<String> required;
        public String format;
        public String description;
        @JsonbProperty("enum")
        public List<String> values;
        public String title;
        public Integer multipleOf;
        public Integer maximum;
        public Integer exclusiveMaximum;
        public Integer minimum;
        public Integer exclusiveMinimum;
        public Integer maxLength;
        public Integer minLength;
        public String pattern;
        public Integer maxItems;
        public Integer minItems;
        public Boolean uniqueItems;
        public Integer maxProperties;
        public Integer minProperties;
        public OpenapiJsonSchema schema;
        public List<OpenapiSchema> oneOf;

        public static OpenapiJsonSchema of(
            OpenapiSchema model)
        {
            OpenapiJsonSchema json = new OpenapiJsonSchema();
            json.type = model.type;
            json.items = model.items;
            json.properties = model.properties != null
                   ? model.properties.entrySet().stream()
                       .collect(Collectors.toMap(Map.Entry::getKey, e -> of(e.getValue())))
                   : null;
            json.required = model.required;
            json.format = model.format;
            json.description = model.description;
            json.values = model.values;
            json.title = model.title;
            json.multipleOf = model.multipleOf;
            json.maximum = Boolean.TRUE.equals(model.exclusiveMaximum) ? null : model.maximum;
            json.exclusiveMaximum = Boolean.TRUE.equals(model.exclusiveMaximum) ? model.maximum : null;
            json.minimum = Boolean.TRUE.equals(model.exclusiveMinimum) ? null : model.minimum;
            json.exclusiveMinimum = Boolean.TRUE.equals(model.exclusiveMinimum) ? model.minimum : null;
            json.maxLength = model.maxLength;
            json.minLength = model.minLength;
            json.pattern = model.pattern;
            json.maxItems = model.maxItems;
            json.minItems = model.minItems;
            json.uniqueItems = model.uniqueItems;
            json.maxProperties = model.maxProperties;
            json.minProperties = model.minProperties;
            json.schema = model.schema != null ? of(model.schema) : null;
            json.oneOf = model.oneOf;
            return json;
        }
    }
}
