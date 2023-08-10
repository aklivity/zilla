/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.http.config;

import static java.util.function.Function.identity;

import java.util.Set;
import java.util.regex.Matcher;

public final class HttpAllowConfig
{
    public final Set<String> origins;
    public final Set<String> methods;
    public final Set<String> headers;
    public final boolean credentials;

    private final Set<String> implicitOrigins;

    public static HttpAllowConfigBuilder<HttpAllowConfig> builder()
    {
        return new HttpAllowConfigBuilder<>(identity());
    }

    HttpAllowConfig(
        Set<String> origins,
        Set<String> methods,
        Set<String> headers,
        boolean credentials)
    {
        this.origins = origins;
        this.implicitOrigins = origins != null ? HttpAccessControlConfig.asImplicitOrigins(origins) : null;
        this.methods = methods;
        this.headers = headers != null ? HttpAccessControlConfig.asCaseless(headers) : null;
        this.credentials = credentials;
    }

    boolean origin(
        String origin)
    {
        return origins == null ||
               origins.contains(origin) ||
               implicitOrigins.contains(origin);
    }

    boolean method(
        String method)
    {
        return methods == null ||
               methods.contains(method);
    }

    boolean headers(
        String headers)
    {
        return headers == null ||
               headersMatch(headers);
    }

    private boolean headersMatch(
        String headers)
    {
        int match = 0;

        Matcher matchHeaders = HttpAccessControlConfig.HEADERS_MATCHER.get().reset(headers);
        while (matchHeaders.find())
        {
            if (header(matchHeaders.group(1)))
            {
                match++;
            }
        }

        return match > 0;
    }

    private boolean header(
        String header)
    {
        return headers == null ||
               headers.contains(header);
    }

    boolean originExplicit()
    {
        return credentials || origins != null;
    }

    boolean methodsExplicit()
    {
        return credentials || methods != null;
    }

    boolean headersExplicit()
    {
        return credentials || headers != null;
    }

    boolean credentialsExplicit()
    {
        return credentials;
    }
}
