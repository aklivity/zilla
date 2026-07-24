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
package io.aklivity.zilla.config.engine.internal;

import static io.aklivity.zilla.config.engine.NamespaceConfigBuilder.BINDINGS_DEFAULT;
import static io.aklivity.zilla.config.engine.NamespaceConfigBuilder.CATALOGS_DEFAULT;
import static io.aklivity.zilla.config.engine.NamespaceConfigBuilder.GUARDS_DEFAULT;
import static io.aklivity.zilla.config.engine.NamespaceConfigBuilder.STORES_DEFAULT;
import static io.aklivity.zilla.config.engine.NamespaceConfigBuilder.TELEMETRY_DEFAULT;
import static io.aklivity.zilla.config.engine.NamespaceConfigBuilder.VAULTS_DEFAULT;

import java.util.Arrays;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.BindingInfoRegistry;
import io.aklivity.zilla.config.engine.CatalogConfig;
import io.aklivity.zilla.config.engine.CatalogInfoRegistry;
import io.aklivity.zilla.config.engine.ExporterInfoRegistry;
import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.engine.GuardInfoRegistry;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.StoreInfoRegistry;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.VaultInfoRegistry;

public class NamespaceAdapter implements JsonbAdapter<NamespaceConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String TELEMETRY_NAME = "telemetry";
    private static final String BINDINGS_NAME = "bindings";
    private static final String CATALOGS_NAME = "catalogs";
    private static final String GUARDS_NAME = "guards";
    private static final String VAULTS_NAME = "vaults";
    private static final String STORES_NAME = "stores";

    private final TelemetryAdapter telemetry;
    private final BindingConfigsAdapter binding;
    private final VaultAdapter vault;
    private final GuardAdapter guard;
    private final CatalogAdapter catalog;
    private final StoreAdapter store;

    public NamespaceAdapter()
    {
        this(null, null, null, null, null, null);
    }

    public NamespaceAdapter(
        BindingInfoRegistry bindingInfos)
    {
        this(bindingInfos, null, null, null, null, null);
    }

    public NamespaceAdapter(
        BindingInfoRegistry bindingInfos,
        CatalogInfoRegistry catalogInfos,
        GuardInfoRegistry guardInfos,
        VaultInfoRegistry vaultInfos,
        ExporterInfoRegistry exporterInfos,
        StoreInfoRegistry storeInfos)
    {
        telemetry = new TelemetryAdapter(exporterInfos);
        binding = new BindingConfigsAdapter(bindingInfos);
        guard = new GuardAdapter(guardInfos);
        vault = new VaultAdapter(vaultInfos);
        catalog = new CatalogAdapter(catalogInfos);
        store = new StoreAdapter(storeInfos);
    }

    @Override
    public JsonObject adaptToJson(
        NamespaceConfig config) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(NAME_NAME, config.name);

        if (!BINDINGS_DEFAULT.equals(config.bindings))
        {
            binding.adaptNamespace(config.name);
            object.add(BINDINGS_NAME, binding.adaptToJson(config.bindings.toArray(BindingConfig[]::new)));
        }

        if (!GUARDS_DEFAULT.equals(config.guards))
        {
            guard.adaptNamespace(config.name);
            JsonObjectBuilder guards = Json.createObjectBuilder();
            for (GuardConfig g : config.guards)
            {
                guards.add(g.name, guard.adaptToJson(g));
            }
            object.add(GUARDS_NAME, guards);
        }

        if (!VAULTS_DEFAULT.equals(config.vaults))
        {
            vault.adaptNamespace(config.name);
            JsonObjectBuilder vaults = Json.createObjectBuilder();
            for (VaultConfig v : config.vaults)
            {
                vaults.add(v.name, vault.adaptToJson(v));
            }
            object.add(VAULTS_NAME, vaults);
        }

        if (!CATALOGS_DEFAULT.equals(config.catalogs))
        {
            catalog.adaptNamespace(config.name);
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogConfig c : config.catalogs)
            {
                catalogs.add(c.name, catalog.adaptToJson(c));
            }
            object.add(CATALOGS_NAME, catalogs);
        }

        if (!STORES_DEFAULT.equals(config.stores))
        {
            store.adaptNamespace(config.name);
            JsonObjectBuilder stores = Json.createObjectBuilder();
            for (StoreConfig s : config.stores)
            {
                stores.add(s.name, store.adaptToJson(s));
            }
            object.add(STORES_NAME, stores);
        }

        if (!TELEMETRY_DEFAULT.equals(config.telemetry))
        {
            telemetry.adaptNamespace(config.name);
            JsonObject telemetry0 = telemetry.adaptToJson(config.telemetry);
            object.add(TELEMETRY_NAME, telemetry0);
        }

        return object.build();
    }

    @Override
    public NamespaceConfig adaptFromJson(
        JsonObject object) throws Exception
    {
        NamespaceConfigBuilder<NamespaceConfig> namespace = NamespaceConfig.builder();
        String name = object.getString(NAME_NAME);

        namespace.name(name);

        if (object.containsKey(TELEMETRY_NAME))
        {
            telemetry.adaptNamespace(name);
            namespace.telemetry(telemetry.adaptFromJson(object.getJsonObject(TELEMETRY_NAME)));
        }

        if (object.containsKey(BINDINGS_NAME))
        {
            binding.adaptNamespace(name);
            namespace.bindings(Arrays.asList(binding.adaptFromJson(object.getJsonObject(BINDINGS_NAME))));
        }

        if (object.containsKey(GUARDS_NAME))
        {
            guard.adaptNamespace(name);
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(GUARDS_NAME).entrySet())
            {
                namespace.guard(guard.adaptFromJson(entry.getKey(), entry.getValue().asJsonObject()));
            }
        }

        if (object.containsKey(VAULTS_NAME))
        {
            vault.adaptNamespace(name);
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(VAULTS_NAME).entrySet())
            {
                namespace.vault(vault.adaptFromJson(entry.getKey(), entry.getValue().asJsonObject()));
            }
        }

        if (object.containsKey(CATALOGS_NAME))
        {
            catalog.adaptNamespace(name);
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(CATALOGS_NAME).entrySet())
            {
                namespace.catalog(catalog.adaptFromJson(entry.getKey(), entry.getValue().asJsonObject()));
            }
        }

        if (object.containsKey(STORES_NAME))
        {
            store.adaptNamespace(name);
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(STORES_NAME).entrySet())
            {
                namespace.store(store.adaptFromJson(entry.getKey(), entry.getValue().asJsonObject()));
            }
        }

        return namespace.build();
    }
}
