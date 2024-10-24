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

import java.util.List;
import java.util.Map;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiItem;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSchema;

@JsonbPropertyOrder({
    "type",
    "items",
    "properties",
    "required"
})
public final class OpenapiSchemaView extends OpenapiResolvable<OpenapiSchema>
{
    private static final String ARRAY_TYPE = "array";

    private final OpenapiSchema schema;
    private final Map<String, OpenapiSchema> schemas;
    private final OpenapiSchema schemaRef;

    private OpenapiSchemaView(
        Map<String, OpenapiSchema> schemas,
        OpenapiSchema schema)
    {
        super(schemas, "#/components/schemas/(\\w+)");
        OpenapiSchema schemaRef = null;
        if (schema.ref != null)
        {
            schemaRef = new OpenapiSchema();
            schemaRef.ref = schema.ref;
            schema = resolveRef(schema.ref);
        }
        else if (ARRAY_TYPE.equals(schema.type) && schema.items != null && schema.items.ref != null)
        {
            schema.items = resolveRef(schema.items.ref);
        }
        this.schemaRef = schemaRef;
        this.schemas = schemas;
        this.schema = schema;
    }

    public String refKey()
    {
        return key;
    }
    public OpenapiSchema ref()
    {
        return schemaRef;
    }

    public String getType()
    {
        return schema.type;
    }

    public OpenapiSchemaView getItems()
    {
        return schema.items == null ? null : OpenapiSchemaView.of(schemas, schema.items);
    }

    public Map<String, OpenapiItem> getProperties()
    {
        return schema.properties;
    }

    public List<String> getRequired()
    {
        return schema.required;
    }

    public static OpenapiSchemaView of(
        Map<String, OpenapiSchema> schemas,
        OpenapiSchema schema)
    {
        return new OpenapiSchemaView(schemas, schema);
    }
}
