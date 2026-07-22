/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig.CROSS_ORIGIN;
import static java.lang.ThreadLocal.withInitial;

import io.aklivity.zilla.runtime.binding.http.config.HttpAccessControlConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpPolicyConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public final class HttpAccessControlResolver
{
    private static final ThreadLocal<HttpHeaderFW.Builder> HEADER_BUILDER = withInitial(HttpHeaderFW.Builder::new);

    private static final String8FW HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = new String8FW("access-control-allow-origin");
    private static final String8FW HEADER_ACCESS_CONTROL_MAX_AGE = new String8FW("access-control-max-age");

    private static final HttpHeaderFW HEADER_ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD =
            new HttpHeaderFW.Builder()
                .wrap(new UnsafeBufferEx(new byte[64]), 0, 64)
                .name("access-control-allow-origin")
                .value("*")
                .build();

    private static final HttpHeaderFW HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS_TRUE =
            new HttpHeaderFW.Builder()
                .wrap(new UnsafeBufferEx(new byte[64]), 0, 64)
                .name("access-control-allow-credentials")
                .value("true")
                .build();

    private final HttpAccessControlConfig access;

    public HttpAccessControlResolver(
        HttpAccessControlConfig access)
    {
        this.access = access;
    }

    public HttpHeaderFW allowOriginHeader(
        HttpPolicyConfig policy,
        String origin)
    {
        HttpHeaderFW allowOrigin = null;

        if (policy == CROSS_ORIGIN)
        {
            allowOrigin = origin != null && access.allowOriginExplicit()
                ? HEADER_BUILDER.get()
                        .wrap(new UnsafeBufferEx(new byte[256]), 0, 256)
                        .name(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN)
                        .value(origin)
                        .build()
                : HEADER_ACCESS_CONTROL_ALLOW_ORIGIN_WILDCARD;
        }

        return allowOrigin;
    }

    public HttpHeaderFW allowCredentialsHeader()
    {
        return access.allowCredentialsExplicit() ? HEADER_ACCESS_CONTROL_ALLOW_CREDENTIALS_TRUE : null;
    }

    public HttpHeaderFW maxAgeHeader()
    {
        return access.maxAge != null
                ? HEADER_BUILDER.get()
                        .wrap(new UnsafeBufferEx(new byte[256]), 0, 256)
                        .name(HEADER_ACCESS_CONTROL_MAX_AGE)
                        .value(Long.toString(access.maxAge.toSeconds()))
                        .build()
                : null;
    }
}
