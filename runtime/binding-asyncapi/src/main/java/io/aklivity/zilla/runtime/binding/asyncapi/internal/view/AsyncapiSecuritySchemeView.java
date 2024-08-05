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

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSecurityScheme;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver.AsyncapiResolver;

public final class AsyncapiSecuritySchemeView
{
    public final String name;
    public final String type;
    public final List<String> scopes;

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
        this.scopes = resolved.scopes;
    }
}
