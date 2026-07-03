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
package io.aklivity.zilla.runtime.common.openapi.config;

public interface OpenapiExtension<T>
{
    enum Scope
    {
        OPENAPI,
        SERVER,
        SERVER_VARIABLE,
        COMPONENTS,
        PATH,
        PARAMETER,
        REQUEST_BODY,
        MEDIA_TYPE,
        ENCODING,
        SCHEMA,
        RESPONSE,
        HEADER,
        LINK,
        SECURITY_SCHEME,
        OPERATION,
        OAUTH_FLOW,
        OAUTH_FLOWS
    }

    Scope scope();

    String name();

    Class<T> type();

    static <T> OpenapiExtension<T> of(
        Scope scope,
        String name,
        Class<T> type)
    {
        return new OpenapiExtension<>()
        {
            @Override
            public Scope scope()
            {
                return scope;
            }

            @Override
            public String name()
            {
                return name;
            }

            @Override
            public Class<T> type()
            {
                return type;
            }
        };
    }
}
