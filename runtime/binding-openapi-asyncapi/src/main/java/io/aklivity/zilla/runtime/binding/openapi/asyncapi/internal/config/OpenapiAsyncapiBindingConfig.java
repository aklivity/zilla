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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.Object2LongHashMap;
import org.agrona.collections.Object2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.parser.AsyncapiParser;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.view.AsyncapiView;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiCatalogConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSchemaConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiSpecificationConfig;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.Openapi;
import io.aklivity.zilla.runtime.binding.openapi.internal.view.OpenapiView;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public final class OpenapiAsyncapiBindingConfig
{
    public final long id;
    public final String name;
    public final KindConfig kind;
    public final OpenapiAsyncapiOptionsConfig options;
    public final List<OpenapiAsyncapiRouteConfig> routes;

    private final OpenapiAsyncNamespaceGenerator namespaceGenerator;
    private final LongFunction<CatalogHandler> supplyCatalog;
    private final ToLongFunction<String> resolvedIds;
    private final Long2LongHashMap compositeResolvedIds;
    private final Object2LongHashMap<String> openapiSchemaIdsByApiId;
    private final Object2LongHashMap<String> asyncapiSchemaIdsByApiId;
    private final AsyncapiParser asyncapiParser;
    private final OpenapiParser openapiParser;
    private final Consumer<NamespaceConfig> attach;
    private final Consumer<NamespaceConfig> detach;

    private NamespaceConfig composite;
    private int httpKafkaOrigin;

    public OpenapiAsyncapiBindingConfig(
        BindingConfig binding,
        OpenapiAsyncNamespaceGenerator namespaceGenerator,
        LongFunction<CatalogHandler> supplyCatalog,
        Consumer<NamespaceConfig> attachComposite,
        Consumer<NamespaceConfig> detachComposite)
    {
        this.id = binding.id;
        this.name = binding.name;
        this.kind = binding.kind;
        this.options = (OpenapiAsyncapiOptionsConfig) binding.options;
        this.resolvedIds = binding.resolveId;
        this.attach = attachComposite;
        this.detach = detachComposite;
        this.openapiSchemaIdsByApiId = new Object2LongHashMap<>(-1);
        this.asyncapiSchemaIdsByApiId = new Object2LongHashMap<>(-1);
        this.compositeResolvedIds = new Long2LongHashMap(-1);
        this.openapiParser = new OpenapiParser();
        this.asyncapiParser = new AsyncapiParser();

        this.routes = binding.routes.stream()
            .map(r -> new OpenapiAsyncapiRouteConfig(r, openapiSchemaIdsByApiId::get))
            .collect(toList());
        this.namespaceGenerator = namespaceGenerator;
        this.supplyCatalog = supplyCatalog;
    }

    public boolean isCompositeOriginId(
        long originId)
    {
        return httpKafkaOrigin == NamespacedId.namespaceId(originId);
    }

    public long resolveResolvedId(
        long apiId)
    {
        return compositeResolvedIds.get(apiId);
    }

    public long resolveAsyncapiApiId(
        String apiId)
    {
        return asyncapiSchemaIdsByApiId.get(apiId);
    }

    public OpenapiAsyncapiRouteConfig resolve(
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
        final List<OpenapiSchemaConfig> openapiConfigs = convertToOpenapi(options.specs.openapi);
        final List<AsyncapiSchemaConfig> asyncapiConfigs = convertToAsyncapi(options.specs.asyncapi);

        final Map<String, OpenapiView> openapis = openapiConfigs.stream()
            .collect(Collectors.toMap(
                c -> c.apiLabel,
                c -> c.openapi,
                (e, n) -> e,
                Object2ObjectHashMap::new));

        final Map<String, AsyncapiView> asyncapis = asyncapiConfigs.stream()
            .collect(Collectors.toMap(
                    c -> c.apiLabel,
                    c -> c.asyncapi,
                    (e, n) -> e,
                    Object2ObjectHashMap::new));

        this.composite = namespaceGenerator.generate(binding, openapis, asyncapis, openapiSchemaIdsByApiId::get);
        attach.accept(this.composite);

        BindingConfig mappingBinding = composite.bindings.stream()
            .filter(b -> b.type.equals("http-kafka")).findFirst().get();

        httpKafkaOrigin = NamespacedId.namespaceId(mappingBinding.id);

        openapiConfigs.forEach(o ->
        {
            compositeResolvedIds.put(o.schemaId, mappingBinding.id);
            openapiSchemaIdsByApiId.put(o.apiLabel, o.schemaId);
        });
        asyncapiConfigs.forEach(a -> asyncapiSchemaIdsByApiId.put(a.apiLabel, a.schemaId));
    }

    public void detach()
    {
        detach.accept(composite);
    }

    private List<AsyncapiSchemaConfig> convertToAsyncapi(
        Set<AsyncapiSpecificationConfig> configs)
    {
        final List<AsyncapiSchemaConfig> asyncapiConfigs = new ArrayList<>();
        for (AsyncapiSpecificationConfig config : configs)
        {
            for (AsyncapiCatalogConfig catalog : config.catalogs)
            {
                final long catalogId = resolvedIds.applyAsLong(catalog.name);
                final CatalogHandler handler = supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                final Asyncapi model = asyncapiParser.parse(payload);
                final AsyncapiView asyncapi = AsyncapiView.of(model);
                asyncapiConfigs.add(new AsyncapiSchemaConfig(config.label, schemaId, asyncapi));
            }
        }
        return asyncapiConfigs;
    }

    private List<OpenapiSchemaConfig> convertToOpenapi(
        Set<OpenapiSpecificationConfig> configs)
    {
        final List<OpenapiSchemaConfig> openapiConfigs = new ArrayList<>();
        for (OpenapiSpecificationConfig config : configs)
        {
            for (OpenapiCatalogConfig catalog : config.catalogs)
            {
                final long catalogId = resolvedIds.applyAsLong(catalog.name);
                final CatalogHandler handler = supplyCatalog.apply(catalogId);
                final int schemaId = handler.resolve(catalog.subject, catalog.version);
                final String payload = handler.resolve(schemaId);
                final Openapi model = openapiParser.parse(payload);
                final OpenapiView openapi = OpenapiView.of(model);
                openapiConfigs.add(new OpenapiSchemaConfig(config.label, schemaId, openapi));
            }
        }
        return openapiConfigs;
    }
}
