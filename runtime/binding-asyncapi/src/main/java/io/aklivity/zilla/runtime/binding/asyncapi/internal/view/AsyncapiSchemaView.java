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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.view;

import java.util.List;
import java.util.Map;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiItem;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;

@JsonbPropertyOrder({
    "type",
    "items",
    "properties",
    "required"
})
public final class AsyncapiSchemaView extends AsyncapiResolvable<AsyncapiSchema>
{
    private static final String ARRAY_TYPE = "array";

    private final AsyncapiSchema schema;
    private final Map<String, AsyncapiSchema> schemas;

    public String getType()
    {
        return schema.type;
    }

    public String refKey()
    {
        return key;
    }

    public AsyncapiSchemaView getItems()
    {
        return schema.items == null ? null : AsyncapiSchemaView.of(schemas, schema.items);
    }

    public Map<String, AsyncapiItem> getProperties()
    {
        return schema.properties;
    }

    public List<String> getRequired()
    {
        return schema.required;
    }

    public static AsyncapiSchemaView of(
        Map<String, AsyncapiSchema> schemas,
        AsyncapiSchema asyncapiSchema)
    {
        return new AsyncapiSchemaView(schemas, asyncapiSchema);
    }

    private AsyncapiSchemaView(
        Map<String, AsyncapiSchema> schemas,
        AsyncapiSchema schema)
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

        if (schema.properties != null)
        {
            for (Map.Entry<String, AsyncapiItem> item : schema.properties.entrySet())
            {
                AsyncapiItem value = item.getValue();
                if (value.ref != null)
                {
                    item.setValue(resolveRef(value.ref));
                }
            }
        }

        this.schemas = schemas;
        this.schema = schema;
    }
}
