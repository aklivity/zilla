/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.config;

import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpAccessControlConfig.HttpPolicyConfig.SAME_ORIGIN;
import static java.lang.ThreadLocal.withInitial;
import static java.util.Collections.unmodifiableSet;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

public final class HttpAccessControlConfig
{
    private static final Pattern ORIGIN_PATTERN = Pattern.compile("(?<scheme>https?)://(?<authority>[^/]+)");
    private static final Pattern HEADERS_PATTERN = Pattern.compile("([^,\\s]+)(:?,\\s*([^,\\\\s]+))*");

    private static final ThreadLocal<Matcher> ORIGIN_MATCHER = withInitial(() -> ORIGIN_PATTERN.matcher(""));
    private static final ThreadLocal<Matcher> HEADERS_MATCHER = withInitial(() -> HEADERS_PATTERN.matcher(""));

    private static final ThreadLocal<HttpHeaderFW.Builder> HEADER_BUILDER = ThreadLocal.withInitial(HttpHeaderFW.Builder::new);

    private static final String8FW HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = new String8FW("access-control-allow-origin");
    private static final String8FW HEADER_ACCESS_CONTROL_MAX_AGE = new String8FW("access-control-max-age");

    private static final HttpHeaderFW HEADER_ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD =
            new HttpHeaderFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .name("access-control-allow-origin")
                .value("*")
                .build();

    private static final HttpHeaderFW HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS_TRUE =
            new HttpHeaderFW.Builder()
                .wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                .name("access-control-allow-credentials")
                .value("true")
                .build();

    private static final Set<String> ALLOWED_REQUEST_HEADERS;

    static
    {
        Set<String> headers = new LinkedHashSet<>();
        headers.add("accept");
        headers.add("accept-language");
        headers.add("content-language");
        headers.add("content-type");
        headers.add("origin");
        ALLOWED_REQUEST_HEADERS = unmodifiableSet(headers);
    }

    private static final Set<String> EXPOSED_RESPONSE_HEADERS;

    static
    {
        Set<String> headers = new LinkedHashSet<>();
        headers.add("cache-control");
        headers.add("content-language");
        headers.add("content-length");
        headers.add("content-type");
        headers.add("expires");
        headers.add("last-modified");
        headers.add("pragma");

        headers.add("server");
        headers.add("date");

        EXPOSED_RESPONSE_HEADERS = unmodifiableSet(headers);
    }

    public enum HttpPolicyConfig
    {
        SAME_ORIGIN,
        CROSS_ORIGIN
    }

    public final HttpPolicyConfig policy;
    public final HttpAllowConfig allow;
    public final Duration maxAge;
    public final HttpExposeConfig expose;

    public HttpAccessControlConfig(
        HttpPolicyConfig policy)
    {
        this.policy = policy;
        this.allow = null;
        this.maxAge = null;
        this.expose = null;
    }

    public HttpAccessControlConfig(
        HttpPolicyConfig policy,
        HttpAllowConfig allow,
        Duration maxAge,
        HttpExposeConfig expose)
    {
        this.policy = CROSS_ORIGIN;
        this.allow = allow;
        this.maxAge = maxAge;
        this.expose = expose;
    }

    public HttpHeaderFW allowOriginHeader(
        String origin)
    {
        HttpHeaderFW allowOrigin = null;

        if (policy == CROSS_ORIGIN)
        {
            allowOrigin = origin != null && allowOriginExplicit()
                ? HEADER_BUILDER.get()
                        .wrap(new UnsafeBuffer(new byte[256]), 0, 256)
                        .name(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)
                        .value(origin)
                        .build()
                : HEADER_ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD;
        }

        return allowOrigin;
    }

    public HttpHeaderFW allowCredentialsHeader()
    {
        return allowCredentialsExplicit() ? HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS_TRUE : null;
    }

    public HttpHeaderFW maxAgeHeader()
    {
        return maxAge != null
                ? HEADER_BUILDER.get()
                        .wrap(new UnsafeBuffer(new byte[256]), 0, 256)
                        .name(HEADER_ACCESS_CONTROL_MAX_AGE)
                        .value(Long.toString(maxAge.toSeconds()))
                        .build()
                : null;
    }

    public boolean allowRequest(
        Map<String, String> headers)
    {
        return policy == CROSS_ORIGIN && allowCrossOrigin(headers) ||
               policy == SAME_ORIGIN && allowSameOrigin(headers);
    }

    public boolean allowOrigin(
        String origin)
    {
        return policy == SAME_ORIGIN || allow == null || allow.origin(origin);
    }

    public boolean allowMethod(
        String method)
    {
        return policy == SAME_ORIGIN || allow == null || allow.method(method);
    }

    public boolean allowHeaders(
        Set<String> headers)
    {
        return policy == SAME_ORIGIN || allow == null || allow.headers(headers, this::allowHeader);
    }

    public boolean allowHeaders(
        String headers)
    {
        return policy == SAME_ORIGIN || allow == null || allow.headers(headers);
    }

    private boolean allowHeader(
        String header)
    {
        return header.charAt(0) == ':' ||
                allow.header(header) ||
                ALLOWED_REQUEST_HEADERS.contains(header);
    }

    public boolean exposeHeader(
        String header)
    {
        return policy == SAME_ORIGIN ||
                header.charAt(0) != ':' &&
                (expose == null || expose.header(header)) &&
                !EXPOSED_RESPONSE_HEADERS.contains(header);
    }

    public boolean allowOriginExplicit()
    {
        return policy == CROSS_ORIGIN && allow != null && allow.originExplicit();
    }

    public boolean allowMethodsExplicit()
    {
        return policy == CROSS_ORIGIN && allow != null && allow.methodsExplicit();
    }

    public boolean allowHeadersExplicit()
    {
        return policy == CROSS_ORIGIN && allow != null && allow.headersExplicit();
    }

    public boolean allowCredentialsExplicit()
    {
        return policy == CROSS_ORIGIN && allow != null && allow.credentialsExplicit();
    }

    public boolean exposeHeaders()
    {
        return policy == CROSS_ORIGIN;
    }

    public boolean exposeHeadersExplicit()
    {
        return policy == CROSS_ORIGIN &&
                (allow != null && allow.credentials ||
                 expose != null && expose.headersExplicit());
    }

    private boolean allowCrossOrigin(
        Map<String, String> headers)
    {
        String origin = headers.get("origin");
        String method = headers.get(":method");
        Set<String> headerNames = headers.keySet();

        return allowOrigin(origin) &&
               allowMethod(method) &&
               allowHeaders(headerNames);
    }

    private boolean allowSameOrigin(
        Map<String, String> headers)
    {
        String origin = headers.get("origin");
        String scheme = headers.get(":scheme");
        String authority = headers.get(":authority");

        final Matcher matcher = ORIGIN_MATCHER.get().reset(origin);
        return matcher.matches() &&
                Objects.equals(matcher.group("scheme"), scheme) &&
                Objects.equals(matcher.group("authority"), authority);
    }

    public static final class HttpAllowConfig
    {
        public final Set<String> origins;
        public final Set<String> methods;
        public final Set<String> headers;
        public final boolean credentials;

        public HttpAllowConfig(
            Set<String> origins,
            Set<String> methods,
            Set<String> headers,
            boolean credentials)
        {
            this.origins = origins;
            this.methods = methods;
            this.headers = headers;
            this.credentials = credentials;
        }

        private boolean origin(
            String origin)
        {
            return origins == null ||
                   origins.contains(origin);
        }

        private boolean method(
            String method)
        {
            return methods == null ||
                   methods.contains(method);
        }

        private boolean headers(
            Set<String> headers,
            Predicate<String> allowHeader)
        {
            return headers == null ||
                   headers.stream().allMatch(allowHeader);
        }

        private boolean headers(
            String headers)
        {
            return headers == null ||
                   headersMatch(headers);
        }

        private boolean headersMatch(
            String headers)
        {
            int match = 0;

            Matcher matchHeaders = HEADERS_MATCHER.get().reset(headers);
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

        private boolean originExplicit()
        {
            return credentials || origins != null;
        }

        private boolean methodsExplicit()
        {
            return credentials || methods != null;
        }

        private boolean headersExplicit()
        {
            return credentials || headers != null;
        }

        public boolean credentialsExplicit()
        {
            return credentials;
        }
    }

    public static final class HttpExposeConfig
    {
        public final Set<String> headers;

        public HttpExposeConfig(
            Set<String> headers)
        {
            this.headers = headers;
        }

        private boolean header(
            String header)
        {
            return headers == null ||
                    headers.contains(header);
        }

        private boolean headersExplicit()
        {
            return headers != null;
        }
    }
}
