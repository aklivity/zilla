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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Map;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiRequestBody;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiRequestBodyView
{
    public final Map<String, OpenapiMediaTypeView> content;
    public final boolean required;

    OpenapiRequestBodyView(
        OpenapiResolver resolver,
        OpenapiRequestBody model)
    {
        OpenapiRequestBody resolved = resolver.requestBodies.resolve(model);

        this.content = model.content != null
                ? model.content.entrySet().stream()
                    .map(e -> new OpenapiMediaTypeView(resolver, e.getKey(), e.getValue()))
                    .collect(toMap(c -> c.name, identity()))
                : null;

        this.required = resolved.required;
    }
}
