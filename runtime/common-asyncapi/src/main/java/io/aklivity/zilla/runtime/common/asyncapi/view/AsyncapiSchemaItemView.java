/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.asyncapi.view;

import java.util.Optional;

import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiMultiFormatSchema;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiSchemaItem;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public abstract class AsyncapiSchemaItemView
{
    public final String name;
    public final AsyncapiSchemaItem model;

    public boolean hasExtension(
        String name)
    {
        return model.extensions != null && model.extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(model.extensions != null ? type.cast(model.extensions.get(name)) : null);
    }

    public static AsyncapiSchemaItemView of(
        AsyncapiResolver resolver,
        AsyncapiSchemaItem model)
    {
        final AsyncapiSchemaItem resolved = resolver.schemas.resolve(model);

        AsyncapiSchemaItemView view = null;

        if (resolved instanceof AsyncapiSchema)
        {
            view = new AsyncapiSchemaView(resolver, (AsyncapiSchema) resolved);
        }
        else if (resolved instanceof AsyncapiMultiFormatSchema)
        {
            view = new AsyncapiMultiFormatSchemaView(resolver, (AsyncapiMultiFormatSchema) resolved);
        }

        return view;
    }

    protected AsyncapiSchemaItemView(
        String name,
        AsyncapiSchemaItem model)
    {
        this.name = name;
        this.model = model;
    }
}
