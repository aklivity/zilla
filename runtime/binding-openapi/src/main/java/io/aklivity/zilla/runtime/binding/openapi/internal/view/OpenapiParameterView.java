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

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiParameter;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public class OpenapiParameterView
{
    public final String name;
    public final String in;
    public final boolean required;
    public final OpenapiSchemaView schema;

    OpenapiParameterView(
        OpenapiResolver resolver,
        OpenapiParameter model)
    {
        OpenapiParameter resolved = resolver.parameters.resolve(model);

        this.name = resolved.name;
        this.in = resolved.in;
        this.required = resolved.required;
        this.schema = new OpenapiSchemaView(resolver, model.schema);
    }
}
