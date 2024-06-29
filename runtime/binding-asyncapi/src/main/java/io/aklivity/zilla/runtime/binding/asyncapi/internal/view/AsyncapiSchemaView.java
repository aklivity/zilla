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

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMultiFormatSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchemaItem;

public final class AsyncapiSchemaView extends AsyncapiResolvable<AsyncapiSchemaItem>
{
    private static final String ARRAY_TYPE = "array";

    private final AsyncapiSchemaItem schema;
    private final Map<String, AsyncapiSchemaItem> schemas;

    public String getType()
    {
        String type = null;
        if (schema instanceof AsyncapiSchema)
        {
            type = ((AsyncapiSchema) schema).type;
        }
        return type;
    }

    public String refKey()
    {
        return key;
    }

    public AsyncapiSchemaView getItems()
    {
        AsyncapiSchemaView view = null;
        if (schema instanceof AsyncapiSchema)
        {
            AsyncapiSchema jsonSchema = (AsyncapiSchema) schema;
            view = jsonSchema.items == null ? null : AsyncapiSchemaView.of(schemas, jsonSchema.items);
        }
        return view;
    }

    public Map<String, AsyncapiSchema> getProperties()
    {
        Map<String, AsyncapiSchema> jsonSchema = null;
        if (schema instanceof AsyncapiSchema)
        {
            jsonSchema = ((AsyncapiSchema) schema).properties;
        }
        return jsonSchema;
    }

    public List<String> getRequired()
    {
        List<String> required = null;
        if (schema instanceof AsyncapiSchema)
        {
            required = ((AsyncapiSchema) schema).required;
        }

        return required;
    }

    public Object getSchema()
    {
        Object schemaValue = null;
        if (schema instanceof AsyncapiMultiFormatSchema)
        {
            schemaValue = ((AsyncapiMultiFormatSchema) schema).schema;
        }
        return schemaValue;
    }

    public static AsyncapiSchemaView of(
        Map<String, AsyncapiSchemaItem> schemas,
        AsyncapiSchemaItem asyncapiSchema)
    {
        return new AsyncapiSchemaView(schemas, asyncapiSchema);
    }

    private AsyncapiSchemaView(
        Map<String, AsyncapiSchemaItem> schemas,
        AsyncapiSchemaItem schema)
    {
        super(schemas, "#/components/schemas/(\\w+)");

        if (schema.ref != null)
        {
            schema = resolveRef(schema.ref);
        }
        else if (schema instanceof AsyncapiSchema)
        {
            schema = resolveAsyncapiSchema((AsyncapiSchema) schema);
        }
        else if (schema instanceof AsyncapiMultiFormatSchema)
        {
            schema = resolveAsyncapiMultiFormatSchema((AsyncapiMultiFormatSchema) schema);
        }

        this.schemas = schemas;
        this.schema = schema;
    }

    private AsyncapiSchema resolveAsyncapiSchema(
        AsyncapiSchema schema)
    {
        if (ARRAY_TYPE.equals(schema.type) && schema.items != null && schema.items.ref != null)
        {
            schema.items = (AsyncapiSchema) resolveRef(schema.items.ref);
        }

        if (schema.properties != null)
        {
            for (Map.Entry<String, AsyncapiSchema> item : schema.properties.entrySet())
            {
                AsyncapiSchema value = item.getValue();
                if (value.ref != null)
                {
                    item.setValue((AsyncapiSchema) resolveRef(value.ref));
                }
            }
        }
        return schema;
    }

    private AsyncapiMultiFormatSchema resolveAsyncapiMultiFormatSchema(
        AsyncapiMultiFormatSchema schema)
    {
        if (schema.ref != null)
        {
            schema = (AsyncapiMultiFormatSchema) resolveRef(schema.ref);
        }
        return schema;
    }
}
