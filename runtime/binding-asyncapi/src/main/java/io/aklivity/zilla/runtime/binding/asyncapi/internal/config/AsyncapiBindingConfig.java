/*
 * Copyright 2021-2023 Aklivity Inc
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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CACHE_CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.PROXY;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Object2LongHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class AsyncapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;

    private final Int2ObjectHashMap<String> typesByNamespaceId;
    private final Int2ObjectHashMap<NamespaceConfig> composites;
    private final Long2LongHashMap apiIdsByNamespaceId;
    private final AsyncapiNamespaceGenerator namespaceGenerator;
    private final Long2LongHashMap compositeResolvedIds;
    private final Object2ObjectHashMap<Matcher, String> paths;
    private final Object2LongHashMap<String> schemaIdsByApiId;
    private final Map<CharSequence, String> operationIds;
    private final LongFunction<CatalogHandler> supplyCatalog;
    private final ToLongFunction<String> resolveId;
    private final Consumer<NamespaceConfig> attach;
    private final Consumer<NamespaceConfig> detach;
    private final long overrideRouteId;
    private final HttpHeaderHelper helper;
    private final AsyncapiParser parser;

    public AsyncapiBindingConfig(
        BindingConfig binding,
        AsyncapiNamespaceGenerator namespaceGenerator,
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
        this.options = (AsyncapiOptionsConfig) binding.options;
        this.composites = new Int2ObjectHashMap<>();
        this.apiIdsByNamespaceId = new Long2LongHashMap(-1);
        this.compositeResolvedIds = new Long2LongHashMap(-1);
        this.schemaIdsByApiId = new Object2LongHashMap<>(-1);
        this.typesByNamespaceId = new Int2ObjectHashMap<>();
        this.paths = new Object2ObjectHashMap<>();
        this.operationIds = new TreeMap<>(CharSequence::compare);
        this.helper = new HttpHeaderHelper();
        this.parser = new AsyncapiParser();
        this.attach = attachComposite;
        this.detach = detachComposite;
        this.routes = binding.routes.stream().map(r -> new AsyncapiRouteConfig(r, schemaIdsByApiId::get)).collect(toList());
    }

    public boolean isCompositeOriginId(
        long originId)
    {
        return typesByNamespaceId.containsKey(NamespacedId.namespaceId(originId));
    }

    public String getCompositeOriginType(
        long originId)
    {
        return typesByNamespaceId.get(NamespacedId.namespaceId(originId));
    }

    public long resolveCompositeResolvedId(
        long apiId)
    {
        return overrideRouteId != -1 ? overrideRouteId : compositeResolvedIds.get(apiId);
    }

    public long resolveApiId(
        long originId)
    {
        return apiIdsByNamespaceId.get(NamespacedId.namespaceId(originId));
    }

    public long resolveApiId(
        String apiId)
    {
        return schemaIdsByApiId.get(apiId);
    }

    public String resolveOperationId(
        HttpBeginExFW httpBeginEx)
    {
        helper.visit(httpBeginEx);

        String operationId = null;

        for (Map.Entry<Matcher, String> item : paths.entrySet())
        {
            Matcher matcher = item.getKey();
            matcher.reset(helper.path);
            if (matcher.find())
            {
                String channelName = item.getValue();
                operationId = operationIds.get(channelName);
                break;
            }
        }

        return operationId;
    }

    public AsyncapiRouteConfig resolve(
        long authorization)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization))
            .findFirst()
            .orElse(null);
    }

    public AsyncapiRouteConfig resolve(
        long authorization,
        long apiId)
    {
        return routes.stream()
            .filter(r -> r.authorized(authorization) && r.matches(apiId))
            .findFirst()
            .orElse(null);
    }

    public void attach(
        BindingConfig binding)
    {
        final List<AsyncapiSchemaConfig> configs = convertToAsyncapi(options.asyncapis);

        if (binding.kind.equals(PROXY))
        {
            attachProxyBinding(binding, configs);
        }
        else
        {
            attachServerClientBinding(binding, configs);
        }

        for (Map.Entry<Integer, NamespaceConfig> entry : composites.entrySet())
        {
            Integer k = entry.getKey();
            NamespaceConfig v = entry.getValue();
            List<BindingConfig> bindings;
            boolean containsSse = v.bindings.stream().anyMatch(b -> b.type.equals("sse"));
            if (containsSse)
            {
                if (binding.kind.equals(SERVER))
                {
                    bindings = v.bindings.stream()
                        .filter(b -> b.type.equals("http") || b.type.equals("http-kafka") || b.type.equals("sse"))
                        .collect(toList());
                }
                else
                {
                    bindings = v.bindings.stream()
                        .filter(b -> b.type.equals("sse"))
                        .collect(toList());
                }
            }
            else
            {
                bindings = v.bindings.stream()
                    .filter(b -> b.type.equals("mqtt") || b.type.equals("http") || b.type.equals("sse") ||
                        b.type.equals("kafka") && b.kind == CACHE_CLIENT || b.type.equals("mqtt-kafka") ||
                        b.type.equals("http-kafka"))
                    .collect(toList());
            }

            extractResolveId(k, bindings);
            extractNamespace(k, bindings);
        }
    }

    public void detach()
    {
        composites.forEach((k, v) -> detach.accept(v));
        composites.clear();
    }

    private void attachProxyBinding(
        BindingConfig binding,
        List<AsyncapiSchemaConfig> configs)
    {
        Map<String, Asyncapi> asyncapis = configs.stream()
                .collect(Collectors.toMap(
                        c -> c.apiLabel,
                        c -> c.asyncapi,
                        (existingValue, newValue) -> existingValue,
                        Object2ObjectHashMap::new));

        namespaceGenerator.init(binding);
        final List<String> labels = configs.stream().map(c -> c.apiLabel).collect(toList());
        final NamespaceConfig composite = namespaceGenerator.generateProxy(binding, asyncapis, schemaIdsByApiId::get, labels);
        composite.readURL = binding.readURL;
        attach.accept(composite);
        updateNamespace(configs, composite, new ArrayList<>(asyncapis.values()));
    }

    private void attachServerClientBinding(
        BindingConfig binding,
        List<AsyncapiSchemaConfig> configs)
    {
        final Map<Integer, AsyncapiNamespaceConfig> namespaceConfigs = new HashMap<>();
        for (AsyncapiSchemaConfig config : configs)
        {
            namespaceGenerator.init(binding);
            Asyncapi asyncapi = config.asyncapi;
            final List<AsyncapiServerView> servers =
                namespaceGenerator.filterAsyncapiServers(asyncapi, options.asyncapis.stream()
                    .filter(a -> a.apiLabel.equals(config.apiLabel))
                    .flatMap(a -> a.servers.stream())
                    .collect(Collectors.toList()));

            servers.stream().collect(Collectors.groupingBy(AsyncapiServerView::getPort)).forEach((k, v) ->
                namespaceConfigs.computeIfAbsent(k, s -> new AsyncapiNamespaceConfig()).addSpecForNamespace(v, config, asyncapi));
        }

        for (AsyncapiNamespaceConfig namespaceConfig : namespaceConfigs.values())
        {
            namespaceConfig.servers.forEach(s -> s.setAsyncapiProtocol(
                namespaceGenerator.resolveProtocol(s.protocol(), options, namespaceConfig.asyncapis, namespaceConfig.servers)));
            final NamespaceConfig composite = namespaceGenerator.generate(binding, namespaceConfig);
            composite.readURL = binding.readURL;
            attach.accept(composite);
            updateNamespace(namespaceConfig.configs, composite, namespaceConfig.asyncapis);
        }
    }

    private void updateNamespace(
        List<AsyncapiSchemaConfig> configs,
        NamespaceConfig composite,
        List<Asyncapi> asyncapis)
    {
        configs.forEach(c ->
        {
            composites.put(c.schemaId, composite);
            schemaIdsByApiId.put(c.apiLabel, c.schemaId);
        });
        asyncapis.forEach(this::extractChannels);
        asyncapis.forEach(this::extractOperations);
    }

    private void extractNamespace(
        int schemaId,
        List<BindingConfig> bindings)
    {
        bindings.forEach(b ->
        {
            final int namespaceId = NamespacedId.namespaceId(b.id);
            typesByNamespaceId.put(namespaceId, b.type);
            apiIdsByNamespaceId.put(namespaceId, schemaId);
        });
    }

    private void extractResolveId(
        int schemaId,
        List<BindingConfig> bindings)
    {
        bindings.forEach(b -> compositeResolvedIds.put(schemaId, b.id));
    }

    private void extractOperations(
        Asyncapi asyncapi)
    {
        asyncapi.operations.forEach((k, v) ->
        {
            String[] refParts = v.channel.ref.split("/");
            operationIds.put(refParts[refParts.length - 1], k);
        });
    }

    private void extractChannels(
        Asyncapi asyncapi)
    {
        asyncapi.channels.forEach((k, v) ->
        {
            String regex = v.address.replaceAll("\\{[^/]+}", "[^/]+");
            regex = "^" + regex + "$";
            Pattern pattern = Pattern.compile(regex);
            paths.put(pattern.matcher(""), k);
        });
    }

    private List<AsyncapiSchemaConfig> convertToAsyncapi(
        List<AsyncapiConfig> configs)
    {
        final List<AsyncapiSchemaConfig> asyncapiConfigs = new ArrayList<>();
        for (AsyncapiConfig config : configs)
        {
            for (AsyncapiCatalogConfig catalog : config.catalogs)
            {
                final long catalogId = resolveId.applyAsLong(catalog.name);
                final CatalogHandler handler = supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                asyncapiConfigs.add(new AsyncapiSchemaConfig(config.apiLabel, schemaId, parser.parse(payload)));
            }
        }
        return asyncapiConfigs;
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
