/*
 * Copyright 2021-2023 Aklivity Inc.
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

import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.util.function.ObjectLongLongFunction;
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
    private final ObjectLongLongFunction<Metric.Kind, LongConsumer> supplyMetricRecorder;
    private final LongConsumer detachBinding;

    public NamespaceRegistry(
        NamespaceConfig namespace,
        Function<String, BindingContext> bindingsByType,
        Function<String, GuardContext> guardsByType,
        Function<String, VaultContext> vaultsByType,
        Function<String, CatalogContext> catalogsByType,
        Function<String, MetricContext> metricsByName,
        Function<String, ExporterContext> exportersByType,
        ToIntFunction<String> supplyLabelId,
        LongFunction<MetricRegistry> supplyMetric,
        LongConsumer exporterAttached,
        LongConsumer exporterDetached,
        ObjectLongLongFunction<Metric.Kind, LongConsumer> supplyMetricRecorder,
        LongConsumer detachBinding)
    {
        this.namespace = namespace;
        this.bindingsByType = bindingsByType;
        this.guardsByType = guardsByType;
        this.vaultsByType = vaultsByType;
        this.catalogsByType = catalogsByType;
        this.metricsByName = metricsByName;
        this.exportersByType = exportersByType;
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
        namespace.telemetry.metrics.forEach(this::attachMetric);
        namespace.telemetry.exporters.forEach(this::attachExporter);
        namespace.bindings.forEach(this::attachBinding);
    }

    public void detach()
    {
        namespace.vaults.forEach(this::detachVault);
        namespace.guards.forEach(this::detachGuard);
        namespace.catalogs.forEach(this::detachCatalog);
        namespace.bindings.forEach(this::detachBinding);
        namespace.telemetry.metrics.forEach(this::detachMetric);
        namespace.telemetry.exporters.forEach(this::detachExporter);
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
            for (long metricId : config.metricIds)
            {
                MetricRegistry metric = supplyMetric.apply(metricId);
                LongConsumer metricRecorder = supplyMetricRecorder.apply(metric.kind(), config.id, metricId);
                MessageConsumer handler = metric.supplyHandler(metricRecorder);
                MetricHandlerKind kind = resolveKind(binding.originTypeId(), binding.routedTypeId(), metric.group());
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
        int bindingId = supplyLabelId.applyAsInt(config.name);
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
        int vaultId = supplyLabelId.applyAsInt(config.name);
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
        int guardId = supplyLabelId.applyAsInt(config.name);
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
        int catalogId = supplyLabelId.applyAsInt(config.name);
        CatalogRegistry context = catalogsById.remove(catalogId);
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
        int metricId = supplyLabelId.applyAsInt(config.name);
        metricsById.remove(metricId);
    }

    private void attachExporter(
        ExporterConfig config)
    {
        int exporterId = supplyLabelId.applyAsInt(config.name);
        ExporterContext context = exportersByType.apply(config.type);
        assert context != null : "Missing exporter type: " + config.type;
        ExporterHandler handler = context.attach(config);
        ExporterRegistry registry = new ExporterRegistry(exporterId, handler, this::onExporterAttached, this::onExporterDetached);
        exportersById.put(exporterId, registry);
        registry.attach();
    }

    private void detachExporter(
        ExporterConfig config)
    {
        int exporterId = supplyLabelId.applyAsInt(config.name);
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
