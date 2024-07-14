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
import java.util.Map;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSecurityScheme;

public final class AsyncapiSecuritySchemeView extends AsyncapiResolvable<AsyncapiSecurityScheme>
{
    private final AsyncapiSecurityScheme scheme;

    public String type()
    {
        return scheme.type;
    }

    public List<String> scopes()
    {
        return scheme.scopes;
    }

    public String refKey()
    {
        return key;
    }

    public static AsyncapiSecuritySchemeView of(
        Map<String, AsyncapiSecurityScheme> schemes,
        AsyncapiSecurityScheme scheme)
    {
        return new AsyncapiSecuritySchemeView(schemes, scheme);
    }

    private AsyncapiSecuritySchemeView(
        Map<String, AsyncapiSecurityScheme> schemes,
        AsyncapiSecurityScheme scheme)
    {
        super(schemes, "#/components/securitySchemes/(.+)");
        this.scheme = scheme.ref == null ? scheme : resolveRef(scheme.ref);
    }
}
