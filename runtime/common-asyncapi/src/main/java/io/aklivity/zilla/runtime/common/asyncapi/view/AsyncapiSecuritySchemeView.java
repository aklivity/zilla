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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.aklivity.zilla.runtime.common.asyncapi.model.AsyncapiSecurityScheme;
import io.aklivity.zilla.runtime.common.asyncapi.model.resolver.AsyncapiResolver;

public final class AsyncapiSecuritySchemeView
{
    public final String name;
    public final String type;
    public final String in;
    public final String scheme;
    public final String bearerFormat;
    public final String parameterName;
    public final List<String> scopes;

    private final Map<String, Object> extensions;

    public boolean hasExtension(
        String name)
    {
        return extensions != null && extensions.containsKey(name);
    }

    public <T> Optional<T> extension(
        String name,
        Class<T> type)
    {
        return Optional.ofNullable(extensions != null ? type.cast(extensions.get(name)) : null);
    }

    AsyncapiSecuritySchemeView(
        AsyncapiResolver resolver,
        AsyncapiSecurityScheme model)
    {
        this(resolver, resolver.securitySchemes.resolveRef(model.ref), model);
    }

    AsyncapiSecuritySchemeView(
        AsyncapiResolver resolver,
        String name,
        AsyncapiSecurityScheme model)
    {
        final AsyncapiSecurityScheme resolved = resolver.securitySchemes.resolve(model);

        this.name = name;
        this.type = resolved.type;
        this.in = resolved.in;
        this.scheme = resolved.scheme;
        this.bearerFormat = resolved.bearerFormat;
        this.parameterName = resolved.name;
        this.scopes = resolved.scopes;
        this.extensions = resolved.extensions;
    }
}
