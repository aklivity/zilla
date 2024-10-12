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
package io.aklivity.zilla.runtime.binding.openapi.internal.view;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiComponents;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiComponentsView
{
    public final List<OpenapiSecuritySchemeView> securitySchemes;
    public final Map<String, OpenapiSchemaItemView> schemas;

    OpenapiComponentsView(
        OpenapiResolver resolver,
        OpenapiComponents model)
    {
        this.securitySchemes = model.securitySchemes != null
                ? model.securitySchemes.entrySet().stream()
                    .map(e -> new OpenapiSecuritySchemeView(resolver, e.getKey(), e.getValue()))
                    .toList()
                : null;

        this.schemas = model.schemas != null
                ? model.schemas.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> OpenapiSchemaItemView.of(resolver, e.getValue())))
                : null;
    }
}
