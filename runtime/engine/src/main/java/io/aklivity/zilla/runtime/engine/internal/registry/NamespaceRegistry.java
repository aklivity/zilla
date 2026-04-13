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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static io.aklivity.zilla.runtime.engine.internal.registry.MetricHandlerKind.ORIGIN;
import static io.aklivity.zilla.runtime.engine.internal.registry.MetricHandlerKind.ROUTED;
import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.BOTH;
import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.RECEIVED;
import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.SENT;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.StoreConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.store.StoreContext;
import io.aklivity.zilla.runtime.engine.util.function.ObjectLongIntIntFunction;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;

public class NamespaceRegistry
{
    private final NamespaceConfig namespace;
    private final Function<String, BindingContext> bindingsByType;
    private final Function<String, GuardContext> guardsByType;
    private final Function<String, VaultContext> vaultsByType;
    private final Function<String, CatalogContext> catalogsByType;
    private final Function<String, MetricContext> metricsByName;
    private final Function<String, ExporterContext> exportersByType;
    private final Function<String, StoreContext> storesByType;
    private final ToIntFunction<String> supplyLabelId;
    private final LongFunction<MetricRegistry> supplyMetric;
    private final LongConsumer exporterAttached;
    private final LongConsumer exporterDetached;
    private final int namespaceId;
    private final Int2ObjectHashMap<BindingRegistry> bindingsById;
    private final Int2ObjectHashMap<GuardRegistry> guardsById;
    private final Int2ObjectHashMap<VaultRegistry> vaultsById;
    private final Int2ObjectHashMap<CatalogRegistry> catalogsById;
    private final Int2ObjectHashMap<MetricRegistry> metricsById;
    private final Int2ObjectHashMap<ExporterRegistry> exportersById;
    private final Int2ObjectHashMap<StoreRegistry> storesById;
    private final ObjectLongIntIntFunction<Metric.Kind, LongConsumer> supplyMetricRecorder;
    private final LongConsumer detachBinding;
    private final Collector collector;
    private final IntFunction<NamespaceRegistry> findNamespace;

    public NamespaceRegistry(
        NamespaceConfig namespace,
        IntFunction<NamespaceRegistry> findNamespace,
        Function<String, BindingContext> bindingsByType,
        Function<String, GuardContext> guardsByType,
        Function<String, VaultContext> vaultsByType,
        Function<String, CatalogContext> catalogsByType,
        Function<String, MetricContext> metricsByName,
        Function<String, ExporterContext> exportersByType,
        Function<String, StoreContext> storesByType,
        ToIntFunction<String> supplyLabelId,
        LongFunction<MetricRegistry> supplyMetric,
        LongConsumer exporterAttached,
        LongConsumer exporterDetached,
        ObjectLongIntIntFunction<Metric.Kind, LongConsumer> supplyMetricRecorder,
        LongConsumer detachBinding,
        Collector collector)
    {
        this.namespace = namespace;
        this.findNamespace = findNamespace;
        this.bindingsByType = bindingsByType;
        this.guardsByType = guardsByType;
        this.vaultsByType = vaultsByType;
        this.catalogsByType = catalogsByType;
        this.metricsByName = metricsByName;
        this.exportersByType = exportersByType;
        this.storesByType = storesByType;
        this.supplyLabelId = supplyLabelId;
        this.supplyMetric = supplyMetric;
        this.supplyMetricRecorder = supplyMetricRecorder;
        this.exporterAttached = exporterAttached;
        this.exporterDetached = exporterDetached;
        this.detachBinding = detachBinding;
        this.namespaceId = supplyLabelId.applyAsInt(namespace.name);
        this.bindingsById = new Int2ObjectHashMap<>();
        this.guardsById = new Int2ObjectHashMap<>();
        this.vaultsById = new Int2ObjectHashMap<>();
        this.catalogsById = new Int2ObjectHashMap<>();
        this.metricsById = new Int2ObjectHashMap<>();
        this.exportersById = new Int2ObjectHashMap<>();
        this.storesById = new Int2ObjectHashMap<>();
        this.collector = collector;
    }

    public int namespaceId()
    {
        return namespaceId;
    }

    public void attach()
    {
        namespace.vaults.forEach(this::attachVault);
        namespace.guards.forEach(this::attachGuard);
        namespace.catalogs.forEach(this::attachCatalog);
        namespace.stores.forEach(this::attachStore);
        namespace.telemetry.metrics.forEach(this::attachMetric);
        namespace.bindings.forEach(this::attachBinding);
        namespace.telemetry.exporters.forEach(this::attachExporter);
    }

    public void detach()
    {
        namespace.vaults.forEach(this::detachVault);
        namespace.guards.forEach(this::detachGuard);
        namespace.catalogs.forEach(this::detachCatalog);
        namespace.stores.forEach(this::detachStore);
        namespace.bindings.forEach(this::detachBinding);
        namespace.telemetry.metrics.forEach(this::detachMetric);
        namespace.telemetry.exporters.forEach(this::detachExporter);
    }

    public Collection<BindingRegistry> bindings()
    {
        return bindingsById.values();
    }

    private void attachBinding(
        BindingConfig config)
    {
        BindingContext context = bindingsByType.apply(config.type);
        assert context != null : "Missing binding type: " + config.type;

        int bindingId = supplyLabelId.applyAsInt(config.name);
        BindingRegistry registry = new BindingRegistry(config, context);
        bindingsById.put(bindingId, registry);
        registry.attach();
        setMetricHandlers(registry, config);
    }

    private void setMetricHandlers(
        BindingRegistry registry,
        BindingConfig config)
    {
        BindingHandler binding = registry.streamFactory();
        MessageConsumer sentOriginMetricHandler = MessageConsumer.NOOP;
        MessageConsumer receivedOriginMetricHandler = MessageConsumer.NOOP;
        MessageConsumer sentRoutedMetricHandler = MessageConsumer.NOOP;
        MessageConsumer receivedRoutedMetricHandler = MessageConsumer.NOOP;
        if (config.metricIds != null)
        {
            List<AttributeConfig> bindingAttributes = config.telemetryRef != null &&
                config.telemetryRef.attributes != null
                ? config.telemetryRef.attributes
                : Collections.emptyList();

            for (long metricId : config.metricIds)
            {
                MetricRegistry metric = supplyMetric.apply(metricId);
                int localMetricId = NamespacedId.localId(metricId);
                IntFunction<LongConsumer> recorderByAttrs = attrId ->
                    supplyMetricRecorder.apply(metric.kind(), config.id, localMetricId, attrId);
                recorderByAttrs.apply(0); // eagerly create default slot in layout
                MessageConsumer handler = bindingAttributes.isEmpty()
                    ? metric.supplyHandler(recorderByAttrs.apply(0))
                    : metric.supplyHandler(recorderByAttrs, bindingAttributes, supplyLabelId);
                int originTypeId = binding.originTypeId() != BindingHandler.STREAM_TYPE
                    ? binding.originTypeId() : (int) config.originTypeId;
                int routedTypeId = binding.routedTypeId() != BindingHandler.STREAM_TYPE
                    ? binding.routedTypeId() : (int) config.routedTypeId;
                MetricHandlerKind kind = resolveKind(originTypeId, routedTypeId, metric.group());
                MetricContext.Direction direction = metric.direction();
                if (kind == ROUTED)
                {
                    if (direction == SENT || direction == BOTH)
                    {
                        sentRoutedMetricHandler = sentRoutedMetricHandler.andThen(handler);
                    }
                    if (direction == RECEIVED || direction == BOTH)
                    {
                        receivedRoutedMetricHandler = receivedRoutedMetricHandler.andThen(handler);
                    }
                }
                else if (kind == ORIGIN)
                {
                    if (direction == SENT || direction == BOTH)
                    {
                        sentOriginMetricHandler = sentOriginMetricHandler.andThen(handler);
                    }
                    if (direction == RECEIVED || direction == BOTH)
                    {
                        receivedOriginMetricHandler = receivedOriginMetricHandler.andThen(handler);
                    }
                }
                else
                {
                    assert kind == null;
                    // no op
                }
            }
        }
        registry.sentOriginMetricHandler(sentOriginMetricHandler);
        registry.receivedOriginMetricHandler(receivedOriginMetricHandler);
        registry.sentRoutedMetricHandler(sentRoutedMetricHandler);
        registry.receivedRoutedMetricHandler(receivedRoutedMetricHandler);
    }

    private MetricHandlerKind resolveKind(
        int originTypeId,
        int routedTypeId,
        String metricGroup)
    {
        MetricHandlerKind kind = null;
        switch (metricGroup)
        {
        case "stream":
            if (originTypeId >= 0)
            {
                kind = ROUTED;
            }
            else if (routedTypeId >= 0)
            {
                kind = ORIGIN;
            }
            break;
        default:
            final int metricGroupId = supplyLabelId.applyAsInt(metricGroup);
            if (metricGroupId == routedTypeId)
            {
                kind = ORIGIN;
            }
            else if (metricGroupId == originTypeId)
            {
                kind = ROUTED;
            }
            break;
        }
        return kind;
    }

    private void detachBinding(
        BindingConfig config)
    {
        int bindingId = NamespacedId.localId(config.id);
        BindingRegistry context = bindingsById.remove(bindingId);
        if (context != null)
        {
            context.detach();
        }
        detachBinding.accept(NamespacedId.id(namespaceId, bindingId));
    }

    private void attachVault(
        VaultConfig config)
    {
        VaultContext context = vaultsByType.apply(config.type);
        assert context != null : "Missing vault type: " + config.type;

        int vaultId = supplyLabelId.applyAsInt(config.name);
        VaultRegistry registry = new VaultRegistry(config, context);
        vaultsById.put(vaultId, registry);
        registry.attach();
    }

    private void detachVault(
        VaultConfig config)
    {
        int vaultId = NamespacedId.localId(config.id);
        VaultRegistry context = vaultsById.remove(vaultId);
        if (context != null)
        {
            context.detach();
        }
    }

    private void attachGuard(
        GuardConfig config)
    {
        GuardContext context = guardsByType.apply(config.type);
        assert context != null : "Missing guard type: " + config.type;

        int vaultId = supplyLabelId.applyAsInt(config.name);
        GuardRegistry registry = new GuardRegistry(config, context);
        guardsById.put(vaultId, registry);
        registry.attach();
    }

    private void detachGuard(
        GuardConfig config)
    {
        int guardId = NamespacedId.localId(config.id);
        GuardRegistry context = guardsById.remove(guardId);
        if (context != null)
        {
            context.detach();
        }
    }

    private void attachCatalog(
        CatalogConfig config)
    {
        CatalogContext context = catalogsByType.apply(config.type);
        assert context != null : "Missing catalog type: " + config.type;

        int catalogId = supplyLabelId.applyAsInt(config.name);
        CatalogRegistry registry = new CatalogRegistry(config, context);
        catalogsById.put(catalogId, registry);
        registry.attach();
    }

    private void detachCatalog(
        CatalogConfig config)
    {
        int catalogId = NamespacedId.localId(config.id);
        CatalogRegistry context = catalogsById.remove(catalogId);
        if (context != null)
        {
            context.detach();
        }
    }

    private void attachStore(
        StoreConfig config)
    {
        StoreContext context = storesByType.apply(config.type);
        assert context != null : "Missing store type: " + config.type;

        int storeId = supplyLabelId.applyAsInt(config.name);
        StoreRegistry registry = new StoreRegistry(config, context);
        storesById.put(storeId, registry);
        registry.attach();
    }

    private void detachStore(
        StoreConfig config)
    {
        int storeId = NamespacedId.localId(config.id);
        StoreRegistry context = storesById.remove(storeId);
        if (context != null)
        {
            context.detach();
        }
    }

    private void attachMetric(
        MetricConfig config)
    {
        int metricId = supplyLabelId.applyAsInt(config.name);
        MetricContext context = metricsByName.apply(config.name);
        MetricRegistry registry = new MetricRegistry(context);
        metricsById.put(metricId, registry);
    }

    private void detachMetric(
        MetricConfig config)
    {
        int metricId = NamespacedId.localId(config.id);
        metricsById.remove(metricId);
    }

    private void attachExporter(
        ExporterConfig config)
    {
        int exporterId = supplyLabelId.applyAsInt(config.name);
        ExporterContext context = exportersByType.apply(config.type);
        assert context != null : "Missing exporter type: " + config.type;
        ExporterHandler handler = context.attach(config, namespace.telemetry.attributes, collector, this::resolveKind);
        ExporterRegistry registry = new ExporterRegistry(exporterId, handler, this::onExporterAttached, this::onExporterDetached);
        exportersById.put(exporterId, registry);
        registry.attach();
    }

    private void detachExporter(
        ExporterConfig config)
    {
        int exporterId = NamespacedId.localId(config.id);
        ExporterRegistry registry = exportersById.remove(exporterId);
        if (registry != null)
        {
            registry.detach();
        }
    }

    BindingRegistry findBinding(
        int bindingId)
    {
        return bindingsById.get(bindingId);
    }

    public KindConfig resolveKind(
        long bindingId)
    {
        int namespaceId = NamespacedId.namespaceId(bindingId);
        NamespaceRegistry namespace = findNamespace.apply(namespaceId);
        int localId = NamespacedId.localId(bindingId);
        BindingRegistry binding = namespace.findBinding(localId);
        return binding == null ? null : binding.kind();
    }

    GuardRegistry findGuard(
        int guardId)
    {
        return guardsById.get(guardId);
    }

    VaultRegistry findVault(
        int vaultId)
    {
        return vaultsById.get(vaultId);
    }

    CatalogRegistry findCatalog(
        int catalogId)
    {
        return catalogsById.get(catalogId);
    }

    StoreRegistry findStore(
        int storeId)
    {
        return storesById.get(storeId);
    }

    MetricRegistry findMetric(
        int metricId)
    {
        return metricsById.get(metricId);
    }

    ExporterRegistry findExporter(
        int exporterId)
    {
        return exportersById.get(exporterId);
    }

    private void onExporterAttached(
        int exporterId)
    {
        exporterAttached.accept(NamespacedId.id(namespaceId, exporterId));
    }

    private void onExporterDetached(
        int exporterId)
    {
        exporterDetached.accept(NamespacedId.id(namespaceId, exporterId));
    }

}
