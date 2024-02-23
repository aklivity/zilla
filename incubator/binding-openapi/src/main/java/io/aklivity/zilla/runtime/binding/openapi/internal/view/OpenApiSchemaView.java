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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import java.util.List;
import java.util.Map;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApiItem;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApiSchema;

@JsonbPropertyOrder({
    "type",
    "items",
    "properties",
    "required"
})
public final class OpenApiSchemaView extends OpenApiResolvable<OpenApiSchema>
{
    private static final String ARRAY_TYPE = "array";

    private final OpenApiSchema schema;
    private final Map<String, OpenApiSchema> schemas;

    private OpenApiSchemaView(
        Map<String, OpenApiSchema> schemas,
        OpenApiSchema schema)
    {
        super(schemas, "#/components/schemas/(\\w+)");
        if (schema.ref != null)
        {
            schema = resolveRef(schema.ref);
        }
        else if (ARRAY_TYPE.equals(schema.type) && schema.items != null && schema.items.ref != null)
        {
            schema.items = resolveRef(schema.items.ref);
        }
        this.schemas = schemas;
        this.schema = schema;
    }

    public String refKey()
    {
        return key;
    }

    public String getType()
    {
        return schema.type;
    }

    public OpenApiSchemaView getItems()
    {
        return schema.items == null ? null : OpenApiSchemaView.of(schemas, schema.items);
    }

    public Map<String, OpenApiItem> getProperties()
    {
        return schema.properties;
    }

    public List<String> getRequired()
    {
        return schema.required;
    }

    public static OpenApiSchemaView of(
        Map<String, OpenApiSchema> schemas,
        OpenApiSchema schema)
    {
        return new OpenApiSchemaView(schemas, schema);
    }
}
