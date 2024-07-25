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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.model.resolver;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiSecurityScheme;

public final class AsyncapiSecuritySchemeResolver
{
    private final Map<String, AsyncapiSecurityScheme> securitySchemes;
    private final Matcher matcher;

    public AsyncapiSecuritySchemeResolver(
        Asyncapi model)
    {
        this.securitySchemes = model.components.securitySchemes;
        this.matcher = Pattern.compile("#/components.securitySchemes/(.+)").matcher("");
    }

    public AsyncapiSecurityScheme resolve(
            AsyncapiSecurityScheme channel)
    {
        AsyncapiSecurityScheme resolved = channel;

        if (channel.ref != null && matcher.reset(channel.ref).matches())
        {
            String key = matcher.group(1);
            resolved = securitySchemes.get(key);
        }

        return resolved;
    }
}
