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

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiHeader;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiHeaderView
{
    public final String name;
    public final boolean required;
    public final boolean allowEmptyValue;
    public final OpenapiSchemaView schema;

    OpenapiHeaderView(
        OpenapiResolver resolver,
        String name,
        OpenapiHeader model)
    {
        this.name = name;

        OpenapiHeader resolved = resolver.headers.resolve(model);

        this.required = resolved.required;
        this.allowEmptyValue = resolved.allowEmptyValue;
        this.schema = new OpenapiSchemaView(resolver, model.schema);
    }

    public boolean hasSchemaFormat()
    {
        return schema != null && schema.format != null;
    }
}
