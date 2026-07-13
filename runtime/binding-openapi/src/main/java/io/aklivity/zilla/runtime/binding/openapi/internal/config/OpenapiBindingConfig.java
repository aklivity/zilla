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
import io.aklivity.zilla.runtime.binding.openapi.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.OpenapiBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.common.openapi.view.OpenapiOperationView;
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
    private static final String8FW HEADER_LOCATION = new String8FW("location");

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
        List<String> tags)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(spec, operation, tags))
            .findFirst()
            .orElse(null);
    }

    public boolean included(
        OpenapiOperationView operation)
    {
        return routes.isEmpty() || routes.stream().anyMatch(r -> r.includes(operation));
    }

    public List<URI> resolveBaseURLs(
        String specLabel)
    {
        final OpenapiSpecificationConfig spec = options.specs.stream()
            .filter(s -> specLabel.equals(s.label))
            .findFirst()
            .orElseThrow();

        return spec.servers.stream()
            .map(server -> OpenapiServerView.resolvePorts(URI.create(server)))
            .collect(toList());
    }

    public URI resolveServer(
        HttpBeginExFW httpBeginEx,
        String specLabel)
    {
        final String scheme = header(httpBeginEx, HEADER_SCHEME);
        final String authority = header(httpBeginEx, HEADER_AUTHORITY);
        final String path = header(httpBeginEx, HEADER_PATH);

        URI server = null;
        if (scheme != null && authority != null && path != null)
        {
            server = resolveBaseURLs(specLabel).stream()
                .filter(s -> scheme.equals(s.getScheme()))
                .filter(s -> authority.equals(authority(s)))
                .filter(s -> path.startsWith(s.getPath()))
                .findFirst()
                .orElse(null);
        }

        return server;
    }

    public HttpBeginExFW resolveLocation(
        HttpBeginExFW httpBeginEx,
        URI server)
    {
        HttpBeginExFW resolved = httpBeginEx;

        if (server != null && header(httpBeginEx, HEADER_LOCATION) != null)
        {
            final HttpBeginExFW.Builder builder = httpBeginExRW
                .wrap(httpExtBuffer, 0, httpExtBuffer.capacity())
                .typeId(httpTypeId);

            httpBeginEx.headers().forEach(h ->
            {
                if (HEADER_LOCATION.equals(h.name()))
                {
                    final String effectiveLocation = OpenapiServerView.requestPath(server, h.value().asString());
                    builder.headersItem(nh -> nh.name(h.name()).value(effectiveLocation));
                }
                else
                {
                    builder.headersItem(nh -> nh.name(h.name()).value(h.value()));
                }
            });

            resolved = builder.build();
        }

        return resolved;
    }

    private static String header(
        HttpBeginExFW httpBeginEx,
        String8FW name)
    {
        final HttpHeaderFW header = httpBeginEx.headers().matchFirst(h -> name.equals(h.name()));
        return header != null ? header.value().asString() : null;
    }

    public OpenapiBeginExFW resolve(
        HttpBeginExFW httpBeginEx,
        OpenapiBeginExFW.Builder openapiBeginExBuilder,
        String operationPath)
    {
        final Flyweight extension = operationPath != null
            ? canonicalize(httpBeginEx, operationPath)
            : httpBeginEx;

        return openapiBeginExBuilder
            .extension(extension.buffer(), extension.offset(), extension.sizeof())
            .build();
    }

    public HttpBeginExFW resolve(
        OpenapiBeginExFW openapiBeginEx,
        HttpBeginExFW.Builder httpBeginExBuilder,
        URI server)
    {
        final HttpBeginExFW canonicalHttpBeginEx = openapiBeginEx.extension().get(httpBeginExRO::tryWrap);

        if (server != null)
        {
            final String scheme = server.getScheme();
            final String authority = authority(server);

            httpBeginExBuilder
                .headersItem(h -> h.name(HEADER_SCHEME).value(scheme))
                .headersItem(h -> h.name(HEADER_AUTHORITY).value(authority));

            canonicalHttpBeginEx.headers().forEach(h ->
            {
                if (HEADER_PATH.equals(h.name()))
                {
                    final String effectivePath = OpenapiServerView.requestPath(server, h.value().asString());
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
        String operationPath)
    {
        final HttpBeginExFW.Builder builder = httpBeginExRW
            .wrap(httpExtBuffer, 0, httpExtBuffer.capacity())
            .typeId(httpTypeId);

        httpBeginEx.headers().forEach(h ->
        {
            if (!HEADER_SCHEME.equals(h.name()) && !HEADER_AUTHORITY.equals(h.name()))
            {
                if (HEADER_PATH.equals(h.name()))
                {
                    final String canonicalPath = operationPath.concat(query(h.value().asString()));
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

    private static String query(
        String path)
    {
        final int queryAt = path.indexOf('?');

        return queryAt != -1 ? path.substring(queryAt) : "";
    }

    private static String authority(
        URI url)
    {
        return url.getPort() != -1
            ? "%s:%d".formatted(url.getHost(), url.getPort())
            : url.getHost();
    }
}
