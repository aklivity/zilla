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
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
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
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiConfiguration;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.MqttBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.types.stream.SseBeginExFW;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiServerView;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class AsyncapiBindingConfig
{
    private static final String SEND_OPERATION = "send";
    private static final String RECEIVE_OPERATION = "receive";

    private static final String MQTT_TYPE_NAME = "mqtt";
    private static final String HTTP_TYPE_NAME = "http";
    private static final String SSE_TYPE_NAME = "sse";

    private final ExtensionFW extensionRO = new ExtensionFW();
    private final HttpBeginExFW httpBeginRO = new HttpBeginExFW();
    private final SseBeginExFW sseBeginRO = new SseBeginExFW();
    private final MqttBeginExFW mqttBeginRO = new MqttBeginExFW();

    public final long id;
    public final String name;
    public final KindConfig kind;
    public final AsyncapiOptionsConfig options;
    public final List<AsyncapiRouteConfig> routes;

    private final long overrideRouteId;
    private final EngineContext context;
    private final ToLongFunction<String> resolveId;
    private final int mqttTypeId;
    private final int httpTypeId;
    private final int sseTypeId;

    private final Int2ObjectHashMap<String> typesByNamespaceId;
    private final Int2ObjectHashMap<CompositeNamespace> composites;
    private final Long2LongHashMap apiIdsByNamespaceId;
    private final AsyncapiNamespaceGenerator namespaceGenerator;
    private final Object2LongHashMap<String> compositeRouteIds;
    private final Object2ObjectHashMap<Matcher, String> paths;
    private final Object2LongHashMap<String> schemaIdsByApiId;
    private final Map<CharSequence, String> operationIds;
    private final Map<String, Map<String, AsyncapiBinding>> operationBindings;
    private final Map<CharSequence, String> receiveOperationIds;
    private final Map<CharSequence, String> sendOperationIds;
    private final HttpHeaderHelper helper;
    private final AsyncapiParser parser;

    public AsyncapiBindingConfig(
        AsyncapiConfiguration config,
        EngineContext context,
        BindingConfig binding,
        AsyncapiNamespaceGenerator namespaceGenerator)
    {
        this.overrideRouteId = config.targetRouteId();
        this.context = context;
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.resolveId = binding.resolveId;
        this.options = (AsyncapiOptionsConfig) binding.options;
        this.mqttTypeId = context.supplyTypeId(MQTT_TYPE_NAME);
        this.httpTypeId = context.supplyTypeId(HTTP_TYPE_NAME);
        this.sseTypeId = context.supplyTypeId(SSE_TYPE_NAME);

        this.namespaceGenerator = namespaceGenerator;
        this.composites = new Int2ObjectHashMap<>();
        this.apiIdsByNamespaceId = new Long2LongHashMap(-1);
        this.compositeRouteIds = new Object2LongHashMap<>(-1);
        this.schemaIdsByApiId = new Object2LongHashMap<>(-1);
        this.typesByNamespaceId = new Int2ObjectHashMap<>();
        this.paths = new Object2ObjectHashMap<>();
        this.operationIds = new TreeMap<>(CharSequence::compare);
        this.operationBindings = new HashMap<>();
        this.receiveOperationIds = new TreeMap<>(CharSequence::compare);
        this.sendOperationIds = new TreeMap<>(CharSequence::compare);
        this.helper = new HttpHeaderHelper();
        this.parser = new AsyncapiParser();
        this.routes = binding.routes.stream()
                .map(r -> new AsyncapiRouteConfig(r, schemaIdsByApiId::get))
                .collect(toList());
    }

    public boolean isCompositeOriginId(
        long originId)
    {
        return typesByNamespaceId.containsKey(NamespacedId.namespaceId(originId));
    }

    public long resolveCompositeRouteId(
        long apiId,
        long operationTypeId)
    {
        return overrideRouteId != -1
                ? overrideRouteId
                : compositeRouteIds.get(String.format("%d/%d", apiId, operationTypeId));
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
        OctetsFW extension)
    {
        final ExtensionFW extensionEx = extension.get(extensionRO::tryWrap);
        final int typeId = extensionEx != null ? extensionEx.typeId() : 0;

        String operationId = null;

        if (typeId == httpTypeId)
        {
            operationId = resolveHttpOperationId(extension);
        }
        else if (typeId == mqttTypeId)
        {
            operationId = resolveMqttOperationId(extension);
        }
        else if (typeId == sseTypeId)
        {
            operationId = resolveSseOperationId(extension);
        }

        return operationId;
    }

    private String resolveMqttOperationId(
        OctetsFW extension)
    {
        final MqttBeginExFW mqttBeginEx = extension.get(mqttBeginRO::tryWrap);

        String operationId = null;

        switch (mqttBeginEx.kind())
        {
        case MqttBeginExFW.KIND_PUBLISH:
        {
            String topic = mqttBeginEx.publish().topic().asString();
            for (Map.Entry<Matcher, String> item : paths.entrySet())
            {
                Matcher matcher = item.getKey();
                matcher.reset(topic);
                if (matcher.find())
                {
                    String channelName = item.getValue();
                    operationId = sendOperationIds.get(channelName);
                    break;
                }
            }
            break;
        }
        case MqttBeginExFW.KIND_SUBSCRIBE:
        {
            String topic = mqttBeginEx.subscribe().filters().matchFirst(x -> true).pattern().asString();
            for (Map.Entry<Matcher, String> item : paths.entrySet())
            {
                Matcher matcher = item.getKey();
                matcher.reset(topic);
                if (matcher.find())
                {
                    String channelName = item.getValue();
                    operationId = receiveOperationIds.get(channelName);
                    break;
                }
            }
            break;
        }
        }

        return operationId;
    }

    private String resolveHttpOperationId(
        OctetsFW extension)
    {
        final HttpBeginExFW httpBeginEx = extension.get(httpBeginRO::tryWrap);

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

    private String resolveSseOperationId(
        OctetsFW extension)
    {
        final SseBeginExFW sseBeginEx = extension.get(sseBeginRO::tryWrap);

        String operationId = null;

        for (Map.Entry<Matcher, String> item : paths.entrySet())
        {
            Matcher matcher = item.getKey();
            matcher.reset(sseBeginEx.path().asString());
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

        for (Map.Entry<Integer, CompositeNamespace> entry : composites.entrySet())
        {
            Integer k = entry.getKey();
            CompositeNamespace v = entry.getValue();
            NamespaceConfig namespaceConfig = v.composite;
            List<BindingConfig> bindings;
            boolean containsSse = namespaceConfig.bindings.stream().anyMatch(b -> b.type.equals("sse"));
            if (containsSse)
            {
                if (binding.kind.equals(SERVER))
                {
                    bindings = namespaceConfig.bindings.stream()
                        .filter(b -> b.type.equals("http") || b.type.equals("http-kafka") || b.type.equals("sse"))
                        .collect(toList());
                }
                else
                {
                    bindings = namespaceConfig.bindings.stream()
                        .filter(b -> b.type.equals("sse"))
                        .collect(toList());
                }
            }
            else
            {
                bindings = namespaceConfig.bindings.stream()
                    .filter(b -> b.type.equals("mqtt") || b.type.equals("http") || b.type.equals("sse") ||
                        b.type.equals("kafka") && b.kind == CACHE_CLIENT || b.type.equals("mqtt-kafka") ||
                        b.type.equals("http-kafka") || b.type.equals("sse-kafka"))
                    .collect(toList());
            }

            extractRouteIds(k, bindings);
            extractNamespace(k, bindings);
        }
    }

    public void detach()
    {
        composites.forEach((k, v) -> context.detachComposite(v.composite));
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
        context.attachComposite(composite);
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
            context.attachComposite(composite);
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
            composites.put(c.schemaId, new CompositeNamespace(composite, c.asyncapi.operations.keySet()));
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

    private void extractRouteIds(
        int schemaId,
        List<BindingConfig> bindings)
    {
        bindings.forEach(b ->
        {
            final String operationType = b.type.replace("-kafka", "");
            final int operationTypeId = context.supplyTypeId(operationType);

            compositeRouteIds.put(String.format("%d/%d", schemaId, operationTypeId), b.id);
        });
    }

    private void extractOperations(
        Asyncapi asyncapi)
    {
        asyncapi.operations.forEach((k, v) ->
        {
            String[] refParts = v.channel.ref.split("/");
            operationIds.put(refParts[refParts.length - 1], k);
            if (SEND_OPERATION.equals(v.action))
            {
                sendOperationIds.put(refParts[refParts.length - 1], k);
            }
            else if (RECEIVE_OPERATION.equals(v.action))
            {
                receiveOperationIds.put(refParts[refParts.length - 1], k);
            }

            operationBindings.put(k, v.bindings);
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
                final CatalogHandler handler = context.supplyCatalog(catalogId);
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

    static class CompositeNamespace
    {
        NamespaceConfig composite;
        Set<String> operations;

        CompositeNamespace(
            NamespaceConfig composite,
            Set<String> operations)
        {
            this.composite = composite;
            this.operations = operations;
        }
    }
}
