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

import java.util.List;
import java.util.Map;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Item;
import io.aklivity.zilla.runtime.command.generate.internal.asyncapi.model.Schema;

@JsonbPropertyOrder({
    "type",
    "items",
    "properties",
    "required"
})
public final class SchemaView extends Resolvable<Schema>
{
    private static final String ARRAY_TYPE = "array";

    private final Schema schema;
    private final Map<String, Schema> schemas;

    private SchemaView(
        Map<String, Schema> schemas,
        Schema schema)
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

    public String getType()
    {
        return schema.type;
    }

    public SchemaView getItems()
    {
        return schema.items == null ? null : SchemaView.of(schemas, schema.items);
    }

    public Map<String, Item> getProperties()
    {
        return schema.properties;
    }

    public List<String> getRequired()
    {
        return schema.required;
    }

    public static SchemaView of(
        Map<String, Schema> schemas,
        Schema schema)
    {
        return new SchemaView(schemas, schema);
    }
}
