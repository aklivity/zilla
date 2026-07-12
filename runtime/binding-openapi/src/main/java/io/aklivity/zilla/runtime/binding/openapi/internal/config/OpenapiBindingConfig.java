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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.OpenapiBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiServerView;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public final class OpenapiBindingConfig
{
    private static final String8FW HEADER_SCHEME = new String8FW(":scheme");
    private static final String8FW HEADER_AUTHORITY = new String8FW(":authority");
    private static final String8FW HEADER_PATH = new String8FW(":path");

    public final long id;
    public final String namespace;
    public final String qname;
    public final String qvault;
    public final KindConfig kind;
    public final OpenapiOptionsConfig options;
    public final List<OpenapiRouteConfig> routes;
    public final List<MetricRefConfig> metricRefs;

    public final ToLongFunction<String> resolveId;
    public final LongFunction<CatalogHandler> supplyCatalog;
    public final ToIntFunction<String> supplyTypeId;
    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;
    public final LongFunction<String> supplyQName;

    public transient OpenapiCompositeConfig composite;

    private final HttpBeginExFW httpBeginExRO;
    private final HttpBeginExFW.Builder httpBeginExRW;
    private final MutableDirectBufferEx httpExtBuffer;
    private final int httpTypeId;

    public OpenapiBindingConfig(
        EngineContext context,
        BindingConfig binding,
        HttpBeginExFW httpBeginExRO,
        HttpBeginExFW.Builder httpBeginExRW,
        MutableDirectBufferEx httpExtBuffer,
        int httpTypeId)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.qvault = binding.qvault;
        this.kind = binding.kind;
        this.options = (OpenapiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
                .map(OpenapiRouteConfig::new)
                .collect(toList());
        this.metricRefs =
                binding.telemetryRef != null && binding.telemetryRef.metricRefs != null
                    ? binding.telemetryRef.metricRefs
                    : List.of();

        this.resolveId = binding.resolveId;
        this.supplyBindingId = context::supplyBindingId;
        this.supplyCatalog = context::supplyCatalog;
        this.supplyTypeId = context::supplyTypeId;
        this.supplyQName = context::supplyQName;

        this.httpBeginExRO = httpBeginExRO;
        this.httpBeginExRW = httpBeginExRW;
        this.httpExtBuffer = httpExtBuffer;
        this.httpTypeId = httpTypeId;

        // TODO: move to engine
        if (options != null)
        {
            if (options.http != null)
            {
                final HttpAuthorizationConfig authorization = options.http.authorization;
                if (authorization != null)
                {
                    final long namespacedId = binding.resolveId.applyAsLong(authorization.name);
                    authorization.qname = context.supplyQName(namespacedId);
                }
            }
        }
    }

    public OpenapiRouteConfig resolve(
        long authorization,
        String spec,
        String operation,
        List<String> tags,
        String serverUrl)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(spec, operation, tags, serverUrl))
            .findFirst()
            .orElse(null);
    }

    public OpenapiBeginExFW resolve(
        HttpBeginExFW httpBeginEx,
        OpenapiBeginExFW.Builder openapiBeginExBuilder,
        OpenapiServerView server)
    {
        final Flyweight extension = server != null ? canonicalize(httpBeginEx, server) : httpBeginEx;

        return openapiBeginExBuilder
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();
    }

    public HttpBeginExFW resolve(
        OpenapiBeginExFW openapiBeginEx,
        HttpBeginExFW.Builder httpBeginExBuilder,
        OpenapiServerView server)
    {
        final HttpBeginExFW canonicalHttpBeginEx = openapiBeginEx.extension().get(httpBeginExRO::tryWrap);

        if (server != null)
        {
            final String scheme = server.url.getScheme();
            final String authority = authority(server.url);
            httpBeginExBuilder
                .headersItem(h -> h.name(HEADER_SCHEME).value(scheme))
                .headersItem(h -> h.name(HEADER_AUTHORITY).value(authority));

            canonicalHttpBeginEx.headers().forEach(h ->
            {
                if (HEADER_PATH.equals(h.name()))
                {
                    final String effectivePath = server.requestPath(h.value().asString());
                    httpBeginExBuilder.headersItem(nh -> nh.name(h.name()).value(effectivePath));
                }
                else
                {
                    httpBeginExBuilder.headersItem(nh -> nh.name(h.name()).value(h.value()));
                }
            });
        }
        else
        {
            canonicalHttpBeginEx.headers().forEach(h ->
                httpBeginExBuilder.headersItem(nh -> nh.name(h.name()).value(h.value())));
        }

        return httpBeginExBuilder.build();
    }

    private HttpBeginExFW canonicalize(
        HttpBeginExFW httpBeginEx,
        OpenapiServerView server)
    {
        final int prefixLength = server.effectivePrefixLength();
        final HttpBeginExFW.Builder builder = httpBeginExRW
            .wrap(httpExtBuffer, 0, httpExtBuffer.capacity())
            .typeId(httpTypeId);

        httpBeginEx.headers().forEach(h ->
        {
            if (!HEADER_SCHEME.equals(h.name()) && !HEADER_AUTHORITY.equals(h.name()))
            {
                if (HEADER_PATH.equals(h.name()))
                {
                    final String canonicalPath = h.value().asString().substring(prefixLength);
                    builder.headersItem(nh -> nh.name(h.name()).value(canonicalPath));
                }
                else
                {
                    builder.headersItem(nh -> nh.name(h.name()).value(h.value()));
                }
            }
        });

        return builder.build();
    }

    private static String authority(
        URI url)
    {
        return url.getPort() != -1
            ? "%s:%d".formatted(url.getHost(), url.getPort())
            : url.getHost();
    }
}
