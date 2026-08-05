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

import static java.util.Collections.emptyList;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class NamespaceConfigBuilder<T> extends ConfigBuilder<T, NamespaceConfigBuilder<T>>
{
    public static final List<BindingConfig> BINDINGS_DEFAULT = emptyList();
    public static final List<CatalogConfig> CATALOGS_DEFAULT = emptyList();
    public static final List<GuardConfig> GUARDS_DEFAULT = emptyList();
    public static final List<VaultConfig> VAULTS_DEFAULT = emptyList();
    public static final List<StoreConfig> STORES_DEFAULT = emptyList();
    public static final TelemetryConfig TELEMETRY_DEFAULT = TelemetryConfig.EMPTY;

    private final Function<NamespaceConfig, T> mapper;

    private String name;
    private TelemetryConfig telemetry;
    private List<BindingConfig> bindings;
    private List<CatalogConfig> catalogs;
    private List<GuardConfig> guards;
    private List<VaultConfig> vaults;
    private List<StoreConfig> stores;

    NamespaceConfigBuilder(
        Function<NamespaceConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<NamespaceConfigBuilder<T>> thisType()
    {
        return (Class<NamespaceConfigBuilder<T>>) getClass();
    }

    public NamespaceConfigBuilder<T> name(
        String name)
    {
        this.name = name;
        return this;
    }

    public TelemetryConfigBuilder<NamespaceConfigBuilder<T>> telemetry()
    {
        return new TelemetryConfigBuilder<>(this::telemetry).namespace(name);
    }

    public NamespaceConfigBuilder<T> telemetry(
        TelemetryConfig telemetry)
    {
        this.telemetry = telemetry;
        return this;
    }

    public GenericBindingConfigBuilder<NamespaceConfigBuilder<T>> binding()
    {
        return new GenericBindingConfigBuilder<>(this::binding).namespace(name);
    }

    public <B extends BindingConfigBuilder<NamespaceConfigBuilder<T>, B, R>, R extends RouteConfigBuilder<B, R>> B binding(
        Function<Function<BindingConfig, NamespaceConfigBuilder<T>>, B> binding)
    {
        return binding.apply(this::binding).namespace(name);
    }

    public NamespaceConfigBuilder<T> binding(
        BindingConfig binding)
    {
        if (bindings == null)
        {
            bindings = new LinkedList<>();
        }
        bindings.add(binding);
        return this;
    }

    public NamespaceConfigBuilder<T> bindings(
        List<BindingConfig> bindings)
    {
        this.bindings = bindings;
        return this;
    }

    public GenericCatalogConfigBuilder<NamespaceConfigBuilder<T>> catalog()
    {
        return new GenericCatalogConfigBuilder<>(this::catalog).namespace(name);
    }

    public <B extends CatalogConfigBuilder<NamespaceConfigBuilder<T>, B>> B catalog(
        Function<Function<CatalogConfig, NamespaceConfigBuilder<T>>, B> catalog)
    {
        return catalog.apply(this::catalog).namespace(name);
    }

    public NamespaceConfigBuilder<T> catalog(
        CatalogConfig catalog)
    {
        if (catalogs == null)
        {
            catalogs = new LinkedList<>();
        }
        catalogs.add(catalog);
        return this;
    }

    public NamespaceConfigBuilder<T> catalogs(
        List<CatalogConfig> catalogs)
    {
        this.catalogs = catalogs;
        return this;
    }

    public GenericGuardConfigBuilder<NamespaceConfigBuilder<T>> guard()
    {
        return new GenericGuardConfigBuilder<>(this::guard).namespace(name);
    }

    public <B extends GuardConfigBuilder<NamespaceConfigBuilder<T>, B>> B guard(
        Function<Function<GuardConfig, NamespaceConfigBuilder<T>>, B> guard)
    {
        return guard.apply(this::guard).namespace(name);
    }

    public NamespaceConfigBuilder<T> guard(
        GuardConfig guard)
    {
        if (guards == null)
        {
            guards = new LinkedList<>();
        }
        guards.add(guard);
        return this;
    }

    public NamespaceConfigBuilder<T> guards(
        List<GuardConfig> guards)
    {
        this.guards = guards;
        return this;
    }

    public GenericVaultConfigBuilder<NamespaceConfigBuilder<T>> vault()
    {
        return new GenericVaultConfigBuilder<>(this::vault).namespace(name);
    }

    public <B extends VaultConfigBuilder<NamespaceConfigBuilder<T>, B>> B vault(
        Function<Function<VaultConfig, NamespaceConfigBuilder<T>>, B> vault)
    {
        return vault.apply(this::vault).namespace(name);
    }

    public NamespaceConfigBuilder<T> vault(
        VaultConfig vault)
    {
        if (vaults == null)
        {
            vaults = new LinkedList<>();
        }
        vaults.add(vault);
        return this;
    }

    public NamespaceConfigBuilder<T> vaults(
        List<VaultConfig> vaults)
    {
        this.vaults = vaults;
        return this;
    }

    public GenericStoreConfigBuilder<NamespaceConfigBuilder<T>> store()
    {
        return new GenericStoreConfigBuilder<>(this::store).namespace(name);
    }

    public <B extends StoreConfigBuilder<NamespaceConfigBuilder<T>, B>> B store(
        Function<Function<StoreConfig, NamespaceConfigBuilder<T>>, B> store)
    {
        return store.apply(this::store).namespace(name);
    }

    public NamespaceConfigBuilder<T> store(
        StoreConfig store)
    {
        if (stores == null)
        {
            stores = new LinkedList<>();
        }
        stores.add(store);
        return this;
    }

    public NamespaceConfigBuilder<T> stores(
        List<StoreConfig> stores)
    {
        this.stores = stores;
        return this;
    }

    public T build()
    {
        return mapper.apply(new NamespaceConfig(
            name,
            Optional.ofNullable(telemetry).orElse(TELEMETRY_DEFAULT),
            Optional.ofNullable(bindings).orElse(BINDINGS_DEFAULT),
            Optional.ofNullable(guards).orElse(GUARDS_DEFAULT),
            Optional.ofNullable(vaults).orElse(VAULTS_DEFAULT),
            Optional.ofNullable(catalogs).orElse(CATALOGS_DEFAULT),
            Optional.ofNullable(stores).orElse(STORES_DEFAULT)));
    }
}
