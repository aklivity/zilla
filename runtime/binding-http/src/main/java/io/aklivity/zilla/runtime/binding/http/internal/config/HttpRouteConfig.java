/*
 * Copyright 2021-2024 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.engine.config.WithConfig.NO_COMPOSITE_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32C;

import io.aklivity.zilla.runtime.binding.http.config.HttpConditionConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpWithConfig;
import io.aklivity.zilla.runtime.binding.http.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.internal.types.String8FW;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public final class HttpRouteConfig
{
    public final long id;

    private final List<HttpConditionMatcher> when;
    private final HttpWithResolver with;
    private final LongObjectPredicate<UnaryOperator<String>> authorized;
    private final Map<String8FW, String16FW> overrides;
    private final LongSupplier supplyInitialId;
    private final LongUnaryOperator supplyInitialIdByHash;

    public HttpRouteConfig(
        RouteConfig route,
        Map<String8FW, String16FW> overrides)
    {
        this(route, overrides, null, null);
    }

    public HttpRouteConfig(
        RouteConfig route,
        Map<String8FW, String16FW> overrides,
        LongSupplier supplyInitialId,
        LongUnaryOperator supplyInitialIdByHash)
    {
        this.id = route.id;
        this.with = Optional.ofNullable(route.with)
            .map(HttpWithConfig.class::cast)
            .map(HttpWithResolver::new)
            .orElse(null);
        this.when = route.when.stream()
            .map(HttpConditionConfig.class::cast)
            .map(HttpConditionMatcher::new)
            .peek(m -> Optional.ofNullable(with).ifPresent(w -> m.observe(w::onConditionMatched)))
            .collect(toList());
        this.authorized = (session, resolve) -> route.authorized.test(session, resolve);
        this.overrides = new LinkedHashMap<>();
        if (overrides != null)
        {
            this.overrides.putAll(overrides);
        }
        this.supplyInitialId = supplyInitialId;
        this.supplyInitialIdByHash = supplyInitialIdByHash;
    }

    public long compositeId()
    {
        return with != null ? with.compositeId() : NO_COMPOSITE_ID;
    }

    public HttpAffinityResolver affinity()
    {
        return with != null ? with.affinity() : null;
    }

    public HttpRouteAffinity resolveAffinity(
        Function<String, String> headers,
        String path)
    {
        final HttpAffinityResolver resolver = affinity();
        final String key = resolver != null
            ? resolver.resolveKey(headers, path)
            : null;
        if (key == null)
        {
            return new HttpRouteAffinity(supplyInitialId.getAsLong(), 0L);
        }
        final CRC32C crc = new CRC32C();
        crc.update(key.getBytes(UTF_8));
        final int hash = (int) crc.getValue();
        return new HttpRouteAffinity(supplyInitialIdByHash.applyAsLong(hash), hash & 0xffff_ffffL);
    }

    public Map<String8FW, String16FW> overrides()
    {
        if (with != null)
        {
            overrides.putAll(with.resolveOverrides());
        }
        return overrides;
    }

    boolean authorized(
        long authorization,
        Function<String, String> headerByName)
    {
        UnaryOperator<String> resolve = input ->
        {
            String format = input.replace("${method}", "%1$s").replace("${path}", "%2$s");
            return format != input
                ? format.formatted(headerByName.apply(":method"), headerByName.apply(":path"))
                : input;
        };

        return authorized.test(authorization, resolve);
    }

    boolean matches(
        Function<String, String> headerByName)
    {
        return when.isEmpty() || when.stream().anyMatch(m -> m.matches(headerByName));
    }
}
