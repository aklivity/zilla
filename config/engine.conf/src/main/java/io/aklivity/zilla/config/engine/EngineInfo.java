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

import java.util.Map;
import java.util.stream.Stream;

import io.aklivity.zilla.config.engine.factory.Factory;

public final class EngineInfo
{
    private final Map<String, BindingInfo> bindings;
    private final Map<String, CatalogInfo> catalogs;
    private final Map<String, GuardInfo> guards;
    private final Map<String, VaultInfo> vaults;
    private final Map<String, ExporterInfo> exporters;
    private final Map<String, StoreInfo> stores;
    private final Map<String, ModelInfo> models;

    public EngineInfo()
    {
        this.bindings = TypedInfoFactory.bindings();
        this.catalogs = TypedInfoFactory.catalogs();
        this.guards = TypedInfoFactory.guards();
        this.vaults = TypedInfoFactory.vaults();
        this.exporters = TypedInfoFactory.exporters();
        this.stores = TypedInfoFactory.stores();
        this.models = TypedInfoFactory.models();
    }

    public Stream<BindingInfo> bindings()
    {
        return bindings.values().stream();
    }

    public BindingInfo binding(
        String type)
    {
        return bindings.get(type);
    }

    public Stream<CatalogInfo> catalogs()
    {
        return catalogs.values().stream();
    }

    public CatalogInfo catalog(
        String type)
    {
        return catalogs.get(type);
    }

    public Stream<GuardInfo> guards()
    {
        return guards.values().stream();
    }

    public GuardInfo guard(
        String type)
    {
        return guards.get(type);
    }

    public Stream<VaultInfo> vaults()
    {
        return vaults.values().stream();
    }

    public VaultInfo vault(
        String type)
    {
        return vaults.get(type);
    }

    public Stream<ExporterInfo> exporters()
    {
        return exporters.values().stream();
    }

    public ExporterInfo exporter(
        String type)
    {
        return exporters.get(type);
    }

    public Stream<StoreInfo> stores()
    {
        return stores.values().stream();
    }

    public StoreInfo store(
        String type)
    {
        return stores.get(type);
    }

    public Stream<ModelInfo> models()
    {
        return models.values().stream();
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
    }
}
