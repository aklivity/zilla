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

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.util.function.ObjectLongLongFunction;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;

public class EngineRegistry
{
    private final Function<String, BindingContext> bindingsByType;
    private final Function<String, GuardContext> guardsByType;
    private final Function<String, VaultContext> vaultsByType;
    private final Function<String, CatalogContext> catalogsByType;
    private final Function<String, MetricContext> metricsByName;
    private final Function<String, ExporterContext> exportersByType;
    private final ToIntFunction<String> supplyLabelId;
    private final LongConsumer exporterAttached;
    private final LongConsumer exporterDetached;
    private final ObjectLongLongFunction<Metric.Kind, LongConsumer> supplyMetricRecorder;
    private final Int2ObjectHashMap<NamespaceRegistry> namespacesById;
    private final LongConsumer detachBinding;
    private final Collector collector;
    private final Consumer<NamespaceConfig> process;

    public EngineRegistry(
        Function<String, BindingContext> bindingsByType,
        Function<String, GuardContext> guardsByType,
        Function<String, VaultContext> vaultsByType,
        Function<String, CatalogContext> catalogsByType,
        Function<String, MetricContext> metricsByName,
        Function<String, ExporterContext> exportersByType,
        ToIntFunction<String> supplyLabelId,
        LongConsumer exporterAttached,
        LongConsumer exporterDetached,
        ObjectLongLongFunction<Metric.Kind, LongConsumer> supplyMetricRecorder,
        LongConsumer detachBinding,
        Collector collector,
        Consumer<NamespaceConfig> process)
    {
        this.bindingsByType = bindingsByType;
        this.guardsByType = guardsByType;
        this.vaultsByType = vaultsByType;
        this.catalogsByType = catalogsByType;
        this.metricsByName = metricsByName;
        this.exportersByType = exportersByType;
        this.supplyLabelId = supplyLabelId;
        this.supplyMetricRecorder = supplyMetricRecorder;
        this.exporterAttached = exporterAttached;
        this.exporterDetached = exporterDetached;
        this.namespacesById = new Int2ObjectHashMap<>();
        this.detachBinding = detachBinding;
        this.collector = collector;
        this.process = process;
    }

    public void process(
        NamespaceConfig composite)
    {
        process.accept(composite);
    }

    public void attachNow(
        NamespaceConfig namespace)
    {
        attach(namespace).run();
    }

    public void detachNow(
        NamespaceConfig namespace)
    {
        detach(namespace).run();
    }

    public NamespaceTask attach(
        NamespaceConfig namespace)
    {
        return new NamespaceTask(namespace, this::attachNamespace);
    }

    public NamespaceTask detach(
        NamespaceConfig namespace)
    {
        return new NamespaceTask(namespace, this::detachNamespace);
    }

    public BindingRegistry resolveBinding(
        long bindingId)
    {
        int namespaceId = NamespacedId.namespaceId(bindingId);
        int localId = NamespacedId.localId(bindingId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findBinding(localId) : null;
    }

    public GuardRegistry resolveGuard(
        long guardId)
    {
        int namespaceId = NamespacedId.namespaceId(guardId);
        int localId = NamespacedId.localId(guardId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findGuard(localId) : null;
    }

    public VaultRegistry resolveVault(
        long vaultId)
    {
        int namespaceId = NamespacedId.namespaceId(vaultId);
        int localId = NamespacedId.localId(vaultId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findVault(localId) : null;
    }

    public CatalogRegistry resolveCatalog(
        long catalogId)
    {
        int namespaceId = NamespacedId.namespaceId(catalogId);
        int localId = NamespacedId.localId(catalogId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findCatalog(localId) : null;
    }

    public MetricRegistry resolveMetric(
        long metricId)
    {
        int namespaceId = NamespacedId.namespaceId(metricId);
        int localId = NamespacedId.localId(metricId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findMetric(localId) : null;
    }

    public ExporterRegistry resolveExporter(
        long exporterId)
    {
        int namespaceId = NamespacedId.namespaceId(exporterId);
        int localId = NamespacedId.localId(exporterId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findExporter(localId) : null;
    }

    public void detachAll()
    {
        namespacesById.values().forEach(NamespaceRegistry::detach);
        namespacesById.clear();
    }

    public Collection<NamespaceRegistry> namespaces()
    {
        return namespacesById.values();
    }

    private NamespaceRegistry findNamespace(
        int namespaceId)
    {
        return namespacesById.get(namespaceId);
    }

    private void attachNamespace(
        NamespaceConfig namespace)
    {
        NamespaceRegistry registry =
                new NamespaceRegistry(namespace, this::findNamespace, bindingsByType, guardsByType, vaultsByType, catalogsByType,
                    metricsByName, exportersByType, supplyLabelId, this::resolveMetric, exporterAttached, exporterDetached,
                    supplyMetricRecorder, detachBinding, collector);
        namespacesById.put(registry.namespaceId(), registry);
        registry.attach();
    }

    protected void detachNamespace(
        NamespaceConfig namespace)
    {
        int namespaceId = supplyLabelId.applyAsInt(namespace.name);
        NamespaceRegistry registry = namespacesById.remove(namespaceId);
        registry.detach();
    }
}
