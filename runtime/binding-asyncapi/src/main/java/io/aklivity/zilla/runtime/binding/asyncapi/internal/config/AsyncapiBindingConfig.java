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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.kafka.config.KafkaAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.mqtt.config.MqttAuthorizationConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.asyncapi.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;

public final class AsyncapiBindingConfig
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
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;
    public final List<MetricRefConfig> metricRefs;

    public final ToLongFunction<String> resolveId;
    public final LongFunction<CatalogHandler> supplyCatalog;
    public final ToIntFunction<String> supplyTypeId;
    public final ToLongBiFunction<NamespaceConfig, BindingConfig> supplyBindingId;
    public final LongFunction<String> supplyQName;

    public transient AsyncapiCompositeConfig composite;

    private final ExtensionFW extensionRO;
    private final HttpBeginExFW httpBeginExRO;
    private final HttpBeginExFW.Builder httpBeginExRW;
    private final MutableDirectBufferEx httpExtBuffer;
    private final int httpTypeId;
    private final SseBeginExFW sseBeginExRO;
    private final SseBeginExFW.Builder sseBeginExRW;
    private final MutableDirectBufferEx sseExtBuffer;
    private final int sseTypeId;

    public AsyncapiBindingConfig(
        EngineContext context,
        BindingConfig binding)
    {
        this(context, binding, new ExtensionFW(),
            new HttpBeginExFW(), new HttpBeginExFW.Builder(), new UnsafeBufferEx(new byte[65536]), 0,
            new SseBeginExFW(), new SseBeginExFW.Builder(), new UnsafeBufferEx(new byte[65536]), 0);
    }

    public AsyncapiBindingConfig(
        EngineContext context,
        BindingConfig binding,
        ExtensionFW extensionRO,
        HttpBeginExFW httpBeginExRO,
        HttpBeginExFW.Builder httpBeginExRW,
        MutableDirectBufferEx httpExtBuffer,
        int httpTypeId,
        SseBeginExFW sseBeginExRO,
        SseBeginExFW.Builder sseBeginExRW,
        MutableDirectBufferEx sseExtBuffer,
        int sseTypeId)
    {
        this.id = binding.id;
        this.namespace = binding.namespace;
        this.qname = binding.qname;
        this.qvault = binding.qvault;
        this.kind = binding.kind;
        this.options = (AsyncapiOptionsConfig) binding.options;
        this.routes = binding.routes.stream()
                .map(AsyncapiRouteConfig::new)
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

        this.extensionRO = extensionRO;
        this.httpBeginExRO = httpBeginExRO;
        this.httpBeginExRW = httpBeginExRW;
        this.httpExtBuffer = httpExtBuffer;
        this.httpTypeId = httpTypeId;
        this.sseBeginExRO = sseBeginExRO;
        this.sseBeginExRW = sseBeginExRW;
        this.sseExtBuffer = sseExtBuffer;
        this.sseTypeId = sseTypeId;

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

            if (options.mqtt != null)
            {
                final MqttAuthorizationConfig authorization = options.mqtt.authorization;
                if (authorization != null)
                {
                    final long namespacedId = binding.resolveId.applyAsLong(authorization.name);
                    authorization.qname = context.supplyQName(namespacedId);
                }
            }

            if (options.kafka != null)
            {
                final KafkaAuthorizationConfig authorization = options.kafka.authorization;
                if (authorization != null)
                {
                    final long namespacedId = binding.resolveId.applyAsLong(authorization.name);
                    authorization.qname = context.supplyQName(namespacedId);
                }
            }
        }
    }

    public AsyncapiRouteConfig resolve(
        long authorization,
        String spec,
        String operation,
        List<String> tags,
        List<AsyncapiServerView> operationServers)
    {
        return routes.stream()
                .filter(r -> r.authorized(authorization) && r.matches(spec, operation, tags, operationServers))
                .findFirst()
                .orElse(null);
    }

    public List<URI> resolveServers(
        String specLabel)
    {
        return options.specs.stream()
            .filter(s -> specLabel.equals(s.label))
            .findFirst()
            .map(s -> s.servers)
            .orElse(List.of())
            .stream()
            .map(URI::create)
            .collect(toList());
    }

    public String resolvePathname(
        List<AsyncapiServerView> servers)
    {
        return servers != null
            ? servers.stream()
                .filter(s -> s.protocol != null && s.protocol.startsWith("http"))
                .filter(s -> s.url != null)
                .map(s -> s.url.getPath())
                .findFirst()
                .orElse("")
            : "";
    }

    public Flyweight canonicalize(
        OctetsFW extension,
        String operationPath)
    {
        Flyweight canonical = extension;

        if (operationPath != null)
        {
            final int typeId = typeId(extension);

            if (typeId == httpTypeId)
            {
                canonical = canonicalizeHttp(extension, operationPath);
            }
            else if (typeId == sseTypeId)
            {
                canonical = canonicalizeSse(extension, operationPath);
            }
        }

        return canonical;
    }

    public Flyweight resolve(
        OctetsFW extension,
        URI server,
        String pathname)
    {
        Flyweight resolved = extension;

        if (server != null)
        {
            final int typeId = typeId(extension);

            if (typeId == httpTypeId)
            {
                resolved = resolveHttp(extension, server, pathname);
            }
            else if (typeId == sseTypeId)
            {
                resolved = resolveSse(extension, server, pathname);
            }
        }

        return resolved;
    }

    public Flyweight resolveLocation(
        OctetsFW extension,
        String pathname)
    {
        Flyweight resolved = extension;

        if (pathname != null && !pathname.isEmpty() && typeId(extension) == httpTypeId)
        {
            resolved = resolveLocationHttp(extension, pathname);
        }

        return resolved;
    }

    private int typeId(
        OctetsFW extension)
    {
        final ExtensionFW peek = extension.get(extensionRO::tryWrap);

        return peek != null ? peek.typeId() : -1;
    }

    private HttpBeginExFW canonicalizeHttp(
        OctetsFW extension,
        String operationPath)
    {
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

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

    private SseBeginExFW canonicalizeSse(
        OctetsFW extension,
        String operationPath)
    {
        final SseBeginExFW sseBeginEx = extension.get(sseBeginExRO::tryWrap);

        return sseBeginExRW
            .wrap(sseExtBuffer, 0, sseExtBuffer.capacity())
            .typeId(sseTypeId)
            .scheme("")
            .authority("")
            .path(operationPath.concat(query(sseBeginEx.path().asString())))
            .lastId(sseBeginEx.lastId())
            .build();
    }

    private HttpBeginExFW resolveHttp(
        OctetsFW extension,
        URI server,
        String pathname)
    {
        final HttpBeginExFW canonicalHttpBeginEx = extension.get(httpBeginExRO::tryWrap);

        final String scheme = server.getScheme();
        final String authority = authority(server);

        final HttpBeginExFW.Builder builder = httpBeginExRW
            .wrap(httpExtBuffer, 0, httpExtBuffer.capacity())
            .typeId(httpTypeId)
            .headersItem(h -> h.name(HEADER_SCHEME).value(scheme))
            .headersItem(h -> h.name(HEADER_AUTHORITY).value(authority));

        canonicalHttpBeginEx.headers().forEach(h ->
        {
            if (HEADER_PATH.equals(h.name()))
            {
                final String effectivePath = requestPath(pathname, h.value().asString());
                builder.headersItem(nh -> nh.name(h.name()).value(effectivePath));
            }
            else
            {
                builder.headersItem(nh -> nh.name(h.name()).value(h.value()));
            }
        });

        return builder.build();
    }

    private HttpBeginExFW resolveLocationHttp(
        OctetsFW extension,
        String pathname)
    {
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginExRO::tryWrap);

        final HttpBeginExFW.Builder builder = httpBeginExRW
            .wrap(httpExtBuffer, 0, httpExtBuffer.capacity())
            .typeId(httpTypeId);

        httpBeginEx.headers().forEach(h ->
        {
            if (HEADER_LOCATION.equals(h.name()))
            {
                final String effectiveLocation = requestPath(pathname, h.value().asString());
                builder.headersItem(nh -> nh.name(h.name()).value(effectiveLocation));
            }
            else
            {
                builder.headersItem(nh -> nh.name(h.name()).value(h.value()));
            }
        });

        return builder.build();
    }

    private SseBeginExFW resolveSse(
        OctetsFW extension,
        URI server,
        String pathname)
    {
        final SseBeginExFW canonicalSseBeginEx = extension.get(sseBeginExRO::tryWrap);

        return sseBeginExRW
            .wrap(sseExtBuffer, 0, sseExtBuffer.capacity())
            .typeId(sseTypeId)
            .scheme(server.getScheme())
            .authority(authority(server))
            .path(requestPath(pathname, canonicalSseBeginEx.path().asString()))
            .lastId(canonicalSseBeginEx.lastId())
            .build();
    }

    private static String requestPath(
        String pathname,
        String path)
    {
        return pathname != null && !pathname.isEmpty()
            ? pathname.endsWith("/") ? pathname.concat(path.substring(1)) : pathname.concat(path)
            : path;
    }

    private static String query(
        String path)
    {
        final int queryAt = path.indexOf('?');

        return queryAt != -1 ? path.substring(queryAt) : "";
    }

    private static String authority(
        URI server)
    {
        return server.getPort() != -1
            ? "%s:%d".formatted(server.getHost(), server.getPort())
            : server.getHost();
    }
}
