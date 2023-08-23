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

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.SAME_ORIGIN;
import static java.lang.ThreadLocal.withInitial;
import static java.util.Collections.unmodifiableSet;
import static java.util.function.Function.identity;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;

public final class HttpAccessControlConfig
{
    private static final Pattern ORIGIN_PATTERN = Pattern.compile("(?<scheme>https?)://(?<authority>[^/]+)");
    private static final Pattern HEADERS_PATTERN = Pattern.compile("([^,\\s]+)(:?,\\s*([^,\\\\s]+))*", CASE_INSENSITIVE);

    private static final ThreadLocal<Matcher> ORIGIN_MATCHER = withInitial(() -> ORIGIN_PATTERN.matcher(""));
    static final ThreadLocal<Matcher> HEADERS_MATCHER = withInitial(() -> HEADERS_PATTERN.matcher(""));

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

    public final HttpPolicyConfig policy;
    public final HttpAllowConfig allow;
    public final Duration maxAge;
    public final HttpExposeConfig expose;

    public static HttpAccessControlConfigBuilder<HttpAccessControlConfig> builder()
    {
        return new HttpAccessControlConfigBuilder<>(identity());
    }

    HttpAccessControlConfig(
        HttpPolicyConfig policy,
        HttpAllowConfig allow,
        Duration maxAge,
        HttpExposeConfig expose)
    {
        this.policy = policy;
        this.allow = allow;
        this.maxAge = maxAge;
        this.expose = expose;
    }

    public HttpPolicyConfig effectivePolicy(
        Map<String, String> headers)
    {
        return policy == SAME_ORIGIN || isSameOrigin(headers) ? SAME_ORIGIN : policy;
    }

    public HttpHeaderFW allowOriginHeader(
        HttpPolicyConfig policy,
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

    public boolean allowPreflight(
        Map<String, String> headers)
    {
        final String origin = headers.get("origin");
        final String requestMethod = headers.get("access-control-request-method");
        final String requestHeaders = headers.get("access-control-request-headers");

        return origin != null && allowOrigin(origin) &&
            (requestMethod == null || allowMethod(requestMethod)) &&
            (requestHeaders == null || allowHeaders(requestHeaders));
    }

    public boolean allowRequest(
        Map<String, String> headers)
    {
        return policy == CROSS_ORIGIN && allowCrossOrigin(headers) ||
               isSameOrigin(headers);
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

    private boolean allowOrigin(
        String origin)
    {
        return policy == SAME_ORIGIN || allow == null || allow.origin(origin);
    }

    private boolean allowMethod(
        String method)
    {
        return policy == SAME_ORIGIN || allow == null || allow.method(method);
    }

    private boolean allowHeaders(
        String headers)
    {
        return policy == SAME_ORIGIN || allow == null || allow.headers(headers);
    }

    private boolean allowCrossOrigin(
        Map<String, String> headers)
    {
        String origin = headers.get("origin");

        return allowOrigin(origin);
    }

    private boolean isSameOrigin(
        Map<String, String> headers)
    {
        String origin = headers.get("origin");
        String scheme = headers.get(":scheme");
        String authority = headers.get(":authority");

        return origin != null && matchesSameOrigin(origin, scheme, authority);
    }

    private boolean matchesSameOrigin(
        String origin,
        String scheme,
        String authority)
    {
        final Matcher matcher = ORIGIN_MATCHER.get().reset(origin);
        return matcher.matches() &&
                matchesSameOrigin(matcher.group("scheme"), scheme, matcher.group("authority"), authority);
    }

    private boolean matchesSameOrigin(
        String originScheme,
        String headerScheme,
        String originAuthority,
        String headerAuthority)
    {
        return Objects.equals(originScheme, headerScheme) &&
                (Objects.equals(originAuthority, headerAuthority) ||
                 matchesAuthority(originScheme, originAuthority, headerAuthority));
    }

    private boolean matchesAuthority(
        String scheme,
        String originAuthority,
        String headerAuthority)
    {
        boolean matches = false;

        if (originAuthority.indexOf(':') == -1 ||
            headerAuthority.indexOf(':') == -1)
        {
            URI originURI = URI.create(String.format("%s://%s", scheme, originAuthority));
            String originHost = originURI.getHost();
            int originPort = asImplicitPortIfNecessary(originURI.getPort(), scheme);
            URI headerURI = URI.create(String.format("%s://%s", scheme, headerAuthority));
            String headerHost = headerURI.getHost();
            int headerPort = asImplicitPortIfNecessary(headerURI.getPort(), scheme);

            matches = Objects.equals(originHost, headerHost) && originPort == headerPort;
        }

        return matches;
    }

    static Set<String> asCaseless(
        Set<String> cased)
    {
        final Set<String> caseless = new TreeSet<String>(String::compareToIgnoreCase);
        caseless.addAll(cased);
        return caseless;
    }

    static Set<String> asImplicitOrigins(
        Set<String> origins)
    {
        Set<String> implicit = new LinkedHashSet<>();

        for (String origin : origins)
        {
            URI originURI = URI.create(origin);
            String scheme = originURI.getScheme();
            String authority = originURI.getAuthority();
            int port = originURI.getPort();

            switch (scheme)
            {
            case "http":
                switch (port)
                {
                case -1:
                    implicit.add(String.format("%s://%s:%d", scheme, authority, 80));
                    break;
                case 80:
                    implicit.add(String.format("%s://%s", scheme, authority));
                    break;
                }
                break;
            case "https":
                switch (port)
                {
                case -1:
                    implicit.add(String.format("%s://%s:%d", scheme, authority, 443));
                    break;
                case 443:
                    implicit.add(String.format("%s://%s", scheme, authority));
                    break;
                }
                break;
            }
        }

        return implicit;
    }

    private static int asImplicitPortIfNecessary(
        int port,
        String scheme)
    {
        int portOrDefault = port;

        if (portOrDefault == -1)
        {
            switch (scheme)
            {
            case "http":
                portOrDefault = 80;
                break;
            case "https":
                portOrDefault = 443;
                break;
            }
        }

        return portOrDefault;
    }
}
