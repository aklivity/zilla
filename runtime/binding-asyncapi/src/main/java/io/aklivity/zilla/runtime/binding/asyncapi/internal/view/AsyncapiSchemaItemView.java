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

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiMultiFormatSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchema;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSchemaItem;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public abstract class AsyncapiSchemaItemView
{
    public final AsyncapiSchemaItem model;

    public static AsyncapiSchemaItemView of(
        AsyncapiResolver resolver,
        AsyncapiSchemaItem model)
    {
        AsyncapiSchemaItemView view = null;

        if (model instanceof AsyncapiSchema)
        {
            view = new AsyncapiSchemaView(resolver, (AsyncapiSchema) model);
        }
        else if (model instanceof AsyncapiMultiFormatSchema)
        {
            view = new AsyncapiMultiFormatSchemaView(resolver, (AsyncapiMultiFormatSchema) model);
        }

        return view;
    }

    protected AsyncapiSchemaItemView(
        AsyncapiSchemaItem model)
    {
        this.model = model;
    }
}
