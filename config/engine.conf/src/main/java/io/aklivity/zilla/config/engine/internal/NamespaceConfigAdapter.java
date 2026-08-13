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
import static java.util.stream.Collectors.toMap;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.BindingInfo;
import io.aklivity.zilla.config.engine.CatalogConfig;
import io.aklivity.zilla.config.engine.CatalogInfo;
import io.aklivity.zilla.config.engine.ConfigAdapter;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.GuardConfig;
import io.aklivity.zilla.config.engine.GuardInfo;
import io.aklivity.zilla.config.engine.NamespaceConfig;
import io.aklivity.zilla.config.engine.NamespaceConfigBuilder;
import io.aklivity.zilla.config.engine.StoreConfig;
import io.aklivity.zilla.config.engine.StoreInfo;
import io.aklivity.zilla.config.engine.VaultConfig;
import io.aklivity.zilla.config.engine.VaultInfo;

public class NamespaceConfigAdapter extends ConfigAdapter<NamespaceConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String TYPE_NAME = "type";
    private static final String TELEMETRY_NAME = "telemetry";
    private static final String BINDINGS_NAME = "bindings";
    private static final String CATALOGS_NAME = "catalogs";
    private static final String GUARDS_NAME = "guards";
    private static final String VAULTS_NAME = "vaults";
    private static final String STORES_NAME = "stores";

    private final TelemetryConfigAdapter telemetry;
    private final Map<String, BindingConfigAdapter> bindingsByType;
    private final Map<String, VaultConfigAdapter> vaultsByType;
    private final Map<String, GuardConfigAdapter> guardsByType;
    private final Map<String, CatalogConfigAdapter> catalogsByType;
    private final Map<String, StoreConfigAdapter> storesByType;

    public NamespaceConfigAdapter(
        EngineInfo info)
    {
        telemetry = new TelemetryConfigAdapter(info);
        bindingsByType = info.bindings().stream().collect(toMap(BindingInfo::type, BindingConfigAdapter::new));
        guardsByType = info.guards().stream().collect(toMap(GuardInfo::type, GuardConfigAdapter::new));
        vaultsByType = info.vaults().stream().collect(toMap(VaultInfo::type, VaultConfigAdapter::new));
        catalogsByType = info.catalogs().stream().collect(toMap(CatalogInfo::type, CatalogConfigAdapter::new));
        storesByType = info.stores().stream().collect(toMap(StoreInfo::type, StoreConfigAdapter::new));
    }

    @Override
    public JsonObject adaptToJson(
        NamespaceConfig config) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(NAME_NAME, config.name);

        if (!BINDINGS_DEFAULT.equals(config.bindings))
        {
            JsonObjectBuilder bindings = Json.createObjectBuilder();
            for (BindingConfig binding : config.bindings)
            {
                BindingConfigAdapter adapter = bindingsByType.get(binding.type);
                assert adapter != null : "unrecognized binding type: " + binding.type;
                bindings.add(binding.name, adapter.adaptToJson(binding));
            }
            object.add(BINDINGS_NAME, bindings);
        }

        if (!GUARDS_DEFAULT.equals(config.guards))
        {
            JsonObjectBuilder guards = Json.createObjectBuilder();
            for (GuardConfig g : config.guards)
            {
                GuardConfigAdapter adapter = guardsByType.get(g.type);
                assert adapter != null : "unrecognized guard type: " + g.type;
                guards.add(g.name, adapter.adaptToJson(g));
            }
            object.add(GUARDS_NAME, guards);
        }

        if (!VAULTS_DEFAULT.equals(config.vaults))
        {
            JsonObjectBuilder vaults = Json.createObjectBuilder();
            for (VaultConfig v : config.vaults)
            {
                VaultConfigAdapter adapter = vaultsByType.get(v.type);
                assert adapter != null : "unrecognized vault type: " + v.type;
                vaults.add(v.name, adapter.adaptToJson(v));
            }
            object.add(VAULTS_NAME, vaults);
        }

        if (!CATALOGS_DEFAULT.equals(config.catalogs))
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            for (CatalogConfig c : config.catalogs)
            {
                CatalogConfigAdapter adapter = catalogsByType.get(c.type);
                assert adapter != null : "unrecognized catalog type: " + c.type;
                catalogs.add(c.name, adapter.adaptToJson(c));
            }
            object.add(CATALOGS_NAME, catalogs);
        }

        if (!STORES_DEFAULT.equals(config.stores))
        {
            JsonObjectBuilder stores = Json.createObjectBuilder();
            for (StoreConfig s : config.stores)
            {
                StoreConfigAdapter adapter = storesByType.get(s.type);
                assert adapter != null : "unrecognized store type: " + s.type;
                stores.add(s.name, adapter.adaptToJson(s));
            }
            object.add(STORES_NAME, stores);
        }

        if (!TELEMETRY_DEFAULT.equals(config.telemetry))
        {
            JsonObject telemetry0 = telemetry.adaptToJson(config.telemetry);
            object.add(TELEMETRY_NAME, telemetry0);
        }

        return object.build();
    }

    @Override
    public NamespaceConfig adaptFromJson(
        JsonObject object) throws Exception
    {
        NamespaceConfigBuilder<NamespaceConfig> builder = NamespaceConfig.builder();
        String namespace = object.getString(NAME_NAME);

        builder.name(namespace);

        if (object.containsKey(TELEMETRY_NAME))
        {
            JsonObject value = object.getJsonObject(TELEMETRY_NAME);
            builder.telemetry(telemetry.adaptFromJson(namespace, value));
        }

        if (object.containsKey(BINDINGS_NAME))
        {
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(BINDINGS_NAME).entrySet())
            {
                String name = entry.getKey();
                JsonObject value = entry.getValue().asJsonObject();

                String type = value.getString(TYPE_NAME);
                BindingConfigAdapter adapter = bindingsByType.get(type);
                assert adapter != null : "unrecognized binding type: " + type;

                builder.binding(adapter.adaptFromJson(namespace, name, value));
            }
        }

        if (object.containsKey(GUARDS_NAME))
        {
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(GUARDS_NAME).entrySet())
            {
                String name = entry.getKey();
                JsonObject value = entry.getValue().asJsonObject();

                String type = value.getString(TYPE_NAME);
                GuardConfigAdapter adapter = guardsByType.get(type);
                assert adapter != null : "unrecognized guard type: " + type;

                builder.guard(adapter.adaptFromJson(namespace, name, value));
            }
        }

        if (object.containsKey(VAULTS_NAME))
        {
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(VAULTS_NAME).entrySet())
            {
                String name = entry.getKey();
                JsonObject value = entry.getValue().asJsonObject();

                String type = value.getString(TYPE_NAME);
                VaultConfigAdapter adapter = vaultsByType.get(type);
                assert adapter != null : "unrecognized vault type: " + type;

                builder.vault(adapter.adaptFromJson(namespace, name, value));
            }
        }

        if (object.containsKey(CATALOGS_NAME))
        {
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(CATALOGS_NAME).entrySet())
            {
                String name = entry.getKey();
                JsonObject value = entry.getValue().asJsonObject();

                String type = value.getString(TYPE_NAME);
                CatalogConfigAdapter adapter = catalogsByType.get(type);
                assert adapter != null : "unrecognized catalog type: " + type;

                builder.catalog(adapter.adaptFromJson(namespace, name, value));
            }
        }

        if (object.containsKey(STORES_NAME))
        {
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(STORES_NAME).entrySet())
            {
                String name = entry.getKey();
                JsonObject value = entry.getValue().asJsonObject();

                String type = value.getString(TYPE_NAME);
                StoreConfigAdapter adapter = storesByType.get(type);
                assert adapter != null : "unrecognized store type: " + type;

                builder.store(adapter.adaptFromJson(namespace, name, value));
            }
        }

        return builder.build();
    }
}
