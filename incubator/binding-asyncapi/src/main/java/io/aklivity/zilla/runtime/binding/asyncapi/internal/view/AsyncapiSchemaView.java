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

    private final AsyncapiSchema asyncapiSchema;
    private final Map<String, AsyncapiSchema> schemas;

    private AsyncapiSchemaView(
        Map<String, AsyncapiSchema> schemas,
        AsyncapiSchema asyncapiSchema)
    {
        super(schemas, "#/components/schemas/(\\w+)");
        if (asyncapiSchema.ref != null)
        {
            asyncapiSchema = resolveRef(asyncapiSchema.ref);
        }
        else if (ARRAY_TYPE.equals(asyncapiSchema.type) && asyncapiSchema.items != null && asyncapiSchema.items.ref != null)
        {
            asyncapiSchema.items = resolveRef(asyncapiSchema.items.ref);
        }
        this.schemas = schemas;
        this.asyncapiSchema = asyncapiSchema;
    }

    public String getType()
    {
        return asyncapiSchema.type;
    }

    public AsyncapiSchemaView getItems()
    {
        return asyncapiSchema.items == null ? null : AsyncapiSchemaView.of(schemas, asyncapiSchema.items);
    }

    public Map<String, AsyncapiItem> getProperties()
    {
        return asyncapiSchema.properties;
    }

    public List<String> getRequired()
    {
        return asyncapiSchema.required;
    }

    public static AsyncapiSchemaView of(
        Map<String, AsyncapiSchema> schemas,
        AsyncapiSchema asyncapiSchema)
    {
        return new AsyncapiSchemaView(schemas, asyncapiSchema);
    }
}
