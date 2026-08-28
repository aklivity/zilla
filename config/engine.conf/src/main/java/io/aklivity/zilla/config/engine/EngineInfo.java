/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.engine;

import static java.util.ServiceLoader.load;
import static java.util.stream.Collectors.toList;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.aklivity.zilla.config.engine.factory.Factory;

public final class EngineInfo
{
    private final Map<String, BindingInfo> bindings;
    private final Map<String, CatalogInfo> catalogs;
    private final Map<String, EmbeddingInfo> embeddings;
    private final Map<String, GuardInfo> guards;
    private final Map<String, VaultInfo> vaults;
    private final Map<String, ExporterInfo> exporters;
    private final Map<String, StoreInfo> stores;
    private final Map<String, ModelInfo> models;
    private final Map<String, MetricGroupInfo> metrics;

    public EngineInfo()
    {
        this.bindings = TypedInfoFactory.bindings();
        this.catalogs = TypedInfoFactory.catalogs();
        this.embeddings = TypedInfoFactory.embeddings();
        this.guards = TypedInfoFactory.guards();
        this.vaults = TypedInfoFactory.vaults();
        this.exporters = TypedInfoFactory.exporters();
        this.stores = TypedInfoFactory.stores();
        this.models = TypedInfoFactory.models();
        this.metrics = TypedInfoFactory.metrics();
    }

    public URL schema()
    {
        return getClass().getResource("schema/engine.schema.json");
    }

    public Collection<URL> patches()
    {
        List<URL> patches = new ArrayList<>();
        patches.addAll(bindings.values().stream().map(BindingInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(catalogs.values().stream().map(CatalogInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(embeddings.values().stream().map(EmbeddingInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(guards.values().stream().map(GuardInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(vaults.values().stream().map(VaultInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(exporters.values().stream().map(ExporterInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(stores.values().stream().map(StoreInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(models.values().stream().map(ModelInfo::schema).filter(Objects::nonNull).collect(toList()));
        patches.addAll(models.values().stream()
            .flatMap(model -> model.extensions().stream())
            .map(ModelExtInfo::schema)
            .filter(Objects::nonNull)
            .collect(toList()));
        patches.addAll(metrics.values().stream().map(MetricGroupInfo::schema).filter(Objects::nonNull).collect(toList()));
        return patches;
    }

    public Collection<BindingInfo> bindings()
    {
        return bindings.values();
    }

    public Collection<CatalogInfo> catalogs()
    {
        return catalogs.values();
    }

    public Collection<EmbeddingInfo> embeddings()
    {
        return embeddings.values();
    }

    public Collection<GuardInfo> guards()
    {
        return guards.values();
    }

    public Collection<VaultInfo> vaults()
    {
        return vaults.values();
    }

    public Collection<ExporterInfo> exporters()
    {
        return exporters.values();
    }

    public Collection<StoreInfo> stores()
    {
        return stores.values();
    }

    public ModelInfo model(
        String type)
    {
        return models.get(type);
    }

    private static final class TypedInfoFactory extends Factory
    {
        private static Map<String, BindingInfo> bindings()
        {
            return instantiate(load(BindingInfo.class), map -> map);
        }

        private static Map<String, CatalogInfo> catalogs()
        {
            return instantiate(load(CatalogInfo.class), map -> map);
        }

        private static Map<String, EmbeddingInfo> embeddings()
        {
            return instantiate(load(EmbeddingInfo.class), map -> map);
        }

        private static Map<String, GuardInfo> guards()
        {
            return instantiate(load(GuardInfo.class), map -> map);
        }

        private static Map<String, VaultInfo> vaults()
        {
            return instantiate(load(VaultInfo.class), map -> map);
        }

        private static Map<String, ExporterInfo> exporters()
        {
            return instantiate(load(ExporterInfo.class), map -> map);
        }

        private static Map<String, StoreInfo> stores()
        {
            return instantiate(load(StoreInfo.class), map -> map);
        }

        private static Map<String, ModelInfo> models()
        {
            return instantiate(load(ModelInfo.class), map -> map);
        }

        private static Map<String, MetricGroupInfo> metrics()
        {
            return instantiate(load(MetricGroupInfo.class), map -> map);
        }
    }
}
