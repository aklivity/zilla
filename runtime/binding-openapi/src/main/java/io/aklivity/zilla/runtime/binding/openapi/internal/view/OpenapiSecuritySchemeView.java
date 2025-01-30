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

import java.util.List;

import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiSecurityScheme;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.resolver.OpenapiResolver;

public final class OpenapiSecuritySchemeView
{
    public final String name;
    public final String type;
    public final String in;
    public final String scheme;
    public final String bearerFormat;
    public final String openidConnectUrl;
    public final List<String> scopes;

    OpenapiSecuritySchemeView(
        OpenapiResolver resolver,
        OpenapiSecurityScheme model)
    {
        this(resolver, resolver.securitySchemes.resolveRef(model.ref), model);
    }

    OpenapiSecuritySchemeView(
        OpenapiResolver resolver,
        String name,
        OpenapiSecurityScheme model)
    {
        final OpenapiSecurityScheme resolved = resolver.securitySchemes.resolve(model);

        this.name = name;
        this.type = resolved.type;
        this.in = resolved.in;
        this.scheme = resolved.scheme;
        this.bearerFormat = resolved.bearerFormat;
        this.openidConnectUrl = resolved.openidConnectUrl;
        this.scopes = List.of(); // TODO: resolved.scopes;
    }
}
