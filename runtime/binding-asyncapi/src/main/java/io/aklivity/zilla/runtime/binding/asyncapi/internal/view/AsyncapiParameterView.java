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

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiParameter;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiParameterView
{
    public final String name;
    public final AsyncapiSchemaView schema;

    AsyncapiParameterView(
        AsyncapiResolver resolver,
        String name,
        AsyncapiParameter model)
    {
        this.name = name;
        this.schema = model.schema != null
            ? new AsyncapiSchemaView(resolver, model.schema)
            : null;
    }
}
