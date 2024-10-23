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

import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenapiPathItem;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.openapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiServerView;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class OpenapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final OpenapiOptionsConfig options;
    public final List<OpenapiRouteConfig> routes;

    private final OpenapiNamespaceGenerator namespaceGenerator;
    private final LongFunction<CatalogHandler> supplyCatalog;
    private final ToLongFunction<String> resolveId;
    private final long overrideRouteId;
    private final IntHashSet httpOrigins;
    private final Long2LongHashMap apiIdsByNamespaceId;
    private final HttpHeaderHelper helper;
    private final OpenapiParser parser;
    private final Consumer<NamespaceConfig> attach;
    private final Consumer<NamespaceConfig> detach;
    private final Long2LongHashMap resolvedIds;
    private final Object2ObjectHashMap<Matcher, OpenapiPathItem> paths;
    private final Int2ObjectHashMap<NamespaceConfig> composites;
    private final Map<CharSequence, Function<OpenapiPathItem, String>> resolversByMethod;

    public OpenapiBindingConfig(
        BindingConfig binding,
        OpenapiNamespaceGenerator namespaceGenerator,
        LongFunction<CatalogHandler> supplyCatalog,
        Consumer<NamespaceConfig> attachComposite,
        Consumer<NamespaceConfig> detachComposite,
        long overrideRouteId)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.namespaceGenerator = namespaceGenerator;
        this.supplyCatalog = supplyCatalog;
        this.overrideRouteId = overrideRouteId;
        this.resolveId = binding.resolveId;
        this.options = (OpenapiOptionsConfig) binding.options;
        this.paths = new Object2ObjectHashMap<>();
        this.composites = new Int2ObjectHashMap<>();
        this.resolvedIds = new Long2LongHashMap(-1);
        this.apiIdsByNamespaceId = new Long2LongHashMap(-1);
        this.httpOrigins = new IntHashSet(-1);
        this.parser = new OpenapiParser();
        this.helper = new HttpHeaderHelper();
        this.attach = attachComposite;
        this.detach = detachComposite;
        this.routes = binding.routes.stream().map(OpenapiRouteConfig::new).collect(toList());

        Map<CharSequence, Function<OpenapiPathItem, String>> resolversByMethod = new TreeMap<>(CharSequence::compare);
        resolversByMethod.put("POST", o -> o.post != null ? o.post.operationId : null);
        resolversByMethod.put("PUT", o -> o.put != null ? o.put.operationId : null);
        resolversByMethod.put("GET", o -> o.get != null ? o.get.operationId : null);
        resolversByMethod.put("DELETE", o -> o.delete != null ? o.delete.operationId : null);
        resolversByMethod.put("OPTIONS", o -> o.options != null ? o.options.operationId : null);
        resolversByMethod.put("HEAD", o -> o.head != null ? o.head.operationId : null);
        resolversByMethod.put("PATCH", o -> o.patch != null ? o.patch.operationId : null);
        resolversByMethod.put("TRACE", o -> o.post != null ? o.trace.operationId : null);
        this.resolversByMethod = unmodifiableMap(resolversByMethod);
    }

    public void attach(
        BindingConfig binding)
    {
        List<OpenapiSchemaConfig> configs = convertToOpenapi(options.openapis);

        final Map<Integer, OpenapiNamespaceConfig> namespaceConfigs = new HashMap<>();
        for (OpenapiSchemaConfig config : configs)
        {
            Openapi openapi = config.openapi;
            final List<OpenapiServerView> servers =
                namespaceGenerator.filterOpenapiServers(openapi.servers, options.openapis.stream()
                    .flatMap(o -> o.servers.stream())
                    .collect(Collectors.toList()));

            servers.stream().collect(Collectors.groupingBy(OpenapiServerView::getPort)).forEach((k, v) ->
                namespaceConfigs.computeIfAbsent(k, s -> new OpenapiNamespaceConfig()).addSpecForNamespace(v, config, openapi));
        }

        for (OpenapiNamespaceConfig namespaceConfig : namespaceConfigs.values())
        {
            final NamespaceConfig composite = namespaceGenerator.generate(binding, namespaceConfig);
            attach.accept(composite);
            namespaceConfig.configs.forEach(c ->
            {
                composites.put(c.schemaId, composite);
                namespaceConfig.openapis.forEach(o ->
                    o.paths.forEach((k, v) ->
                    {
                        String regex = k.replaceAll("\\{[^/]+}", "[^/]+");
                        regex = "^" + regex + "$";
                        Pattern pattern = Pattern.compile(regex);
                        paths.put(pattern.matcher(""), v);
                    })
                );
            });
        }

        composites.forEach((k, v) ->
        {
            List<BindingConfig> http = v.bindings.stream().filter(b -> b.type.equals("http")).collect(toList());
            http.forEach(b -> resolvedIds.put(k, b.id));
            http.stream()
                .map(b -> NamespacedId.namespaceId(b.id))
                .forEach(n ->
                {
                    httpOrigins.add(n);
                    apiIdsByNamespaceId.put(n, k);
                });
        });
    }

    public void detach()
    {
        composites.forEach((k, v) -> detach.accept(v));
        composites.clear();
    }

    public boolean isCompositeOriginId(
        long originId)
    {
        return httpOrigins.contains(NamespacedId.namespaceId(originId));
    }

    public long resolveApiId(
        long originId)
    {
        return apiIdsByNamespaceId.get(NamespacedId.namespaceId(originId));
    }

    public long resolveResolvedId(
        long apiId)
    {
        return overrideRouteId != -1 ? overrideRouteId : resolvedIds.get(apiId);
    }

    public String resolveOperationId(
        HttpBeginExFW httpBeginEx)
    {
        helper.visit(httpBeginEx);

        String operationId = null;

        resolve:
        for (Map.Entry<Matcher, OpenapiPathItem> item : paths.entrySet())
        {
            Matcher matcher = item.getKey();
            matcher.reset(helper.path);
            if (matcher.find())
            {
                OpenapiPathItem operations = item.getValue();
                operationId = resolveMethod(operations);
                break resolve;
            }
        }

        return operationId;
    }

    private String resolveMethod(
        OpenapiPathItem operations)
    {
        Function<OpenapiPathItem, String> resolver = resolversByMethod.get(helper.method);
        return resolver != null ? resolver.apply(operations) : null;
    }

    public OpenapiRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    private List<OpenapiSchemaConfig> convertToOpenapi(
        List<OpenapiConfig> configs)
    {
        final List<OpenapiSchemaConfig> openapiConfigs = new ArrayList<>();
        for (OpenapiConfig config : configs)
        {
            for (OpenapiCatalogConfig catalog : config.catalogs)
            {
                final long catalogId = resolveId.applyAsLong(catalog.name);
                final CatalogHandler handler = supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                openapiConfigs.add(new OpenapiSchemaConfig(config.apiLabel, schemaId, parser.parse(payload)));
            }
        }
        return openapiConfigs;
    }

    private static final class HttpHeaderHelper
    {
        private static final String8FW HEADER_NAME_METHOD = new String8FW(":method");
        private static final String8FW HEADER_NAME_PATH = new String8FW(":path");
        private static final String8FW HEADER_NAME_SCHEME = new String8FW(":scheme");
        private static final String8FW HEADER_NAME_AUTHORITY = new String8FW(":authority");

        private final Map<String8FW, Consumer<String16FW>> visitors;
        {
            Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
            visitors.put(HEADER_NAME_METHOD, this::visitMethod);
            visitors.put(HEADER_NAME_PATH, this::visitPath);
            visitors.put(HEADER_NAME_SCHEME, this::visitScheme);
            visitors.put(HEADER_NAME_AUTHORITY, this::visitAuthority);
            this.visitors = visitors;
        }
        private final AsciiSequenceView methodRO = new AsciiSequenceView();
        private final AsciiSequenceView pathRO = new AsciiSequenceView();
        private final String16FW schemeRO = new String16FW();
        private final String16FW authorityRO = new String16FW();

        public CharSequence path;
        public CharSequence method;
        public String16FW scheme;
        public String16FW authority;

        private void visit(
            HttpBeginExFW beginEx)
        {
            method = null;
            path = null;
            scheme = null;
            authority = null;

            if (beginEx != null)
            {
                beginEx.headers().forEach(this::dispatch);
            }
        }

        private boolean dispatch(
            HttpHeaderFW header)
        {
            final String8FW name = header.name();
            final Consumer<String16FW> visitor = visitors.get(name);
            if (visitor != null)
            {
                visitor.accept(header.value());
            }

            return method != null &&
                path != null &&
                scheme != null &&
                authority != null;
        }

        private void visitMethod(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            method = methodRO.wrap(buffer, offset, length);
        }

        private void visitPath(
            String16FW value)
        {
            final DirectBuffer buffer = value.buffer();
            final int offset = value.offset() + value.fieldSizeLength();
            final int length = value.sizeof() - value.fieldSizeLength();
            path = pathRO.wrap(buffer, offset, length);
        }

        private void visitScheme(
            String16FW value)
        {
            scheme = schemeRO.wrap(value.buffer(), value.offset(), value.limit());
        }

        private void visitAuthority(
            String16FW value)
        {
            authority = authorityRO.wrap(value.buffer(), value.offset(), value.limit());
        }
    }
}
