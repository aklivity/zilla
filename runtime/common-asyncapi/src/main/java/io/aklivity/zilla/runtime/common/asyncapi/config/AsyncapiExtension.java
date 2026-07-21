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
package io.aklivity.zilla.runtime.common.asyncapi.config;

public interface AsyncapiExtension
{
    enum Scope
    {
        ASYNCAPI,
        SERVER,
        SERVER_VARIABLE,
        CHANNEL,
        OPERATION,
        MESSAGE,
        TRAIT,
        PARAMETER,
        COMPONENTS,
        SCHEMA,
        SECURITY_SCHEME,
        CORRELATION_ID,
        REPLY
    }

    Scope scope();

    String name();

    Class<?> type();

    static <T> AsyncapiExtension of(
        Scope scope,
        String name,
        Class<T> type)
    {
        return new AsyncapiExtension()
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
            public Class<?> type()
            {
                return type;
            }
        };
    }
}
