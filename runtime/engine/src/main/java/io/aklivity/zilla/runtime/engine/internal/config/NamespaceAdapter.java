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
package io.aklivity.zilla.runtime.engine.internal.config;

import static io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder.BINDINGS_DEFAULT;
import static io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder.CATALOGS_DEFAULT;
import static io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder.GUARDS_DEFAULT;
import static io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder.TELEMETRY_DEFAULT;
import static io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder.VAULTS_DEFAULT;

import java.util.Arrays;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfigBuilder;

public class NamespaceAdapter implements JsonbAdapter<NamespaceConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String TELEMETRY_NAME = "telemetry";
    private static final String BINDINGS_NAME = "bindings";
    private static final String CATALOGS_NAME = "catalogs";
    private static final String GUARDS_NAME = "guards";
    private static final String VAULTS_NAME = "vaults";

    private final TelemetryAdapter telemetry;
    private final BindingConfigsAdapter binding;
    private final VaultAdapter vault;
    private final GuardAdapter guard;
    private final CatalogAdapter catalog;

    public NamespaceAdapter(
        ConfigAdapterContext context)
    {
        telemetry = new TelemetryAdapter(context);
        binding = new BindingConfigsAdapter(context);
        guard = new GuardAdapter(context);
        vault = new VaultAdapter(context);
        catalog = new CatalogAdapter(context);
    }

    @Override
    public JsonObject adaptToJson(
        NamespaceConfig config) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        object.add(NAME_NAME, config.name);

        if (!BINDINGS_DEFAULT.equals(config.bindings))
        {
            object.add(BINDINGS_NAME, binding.adaptToJson(config.bindings.toArray(BindingConfig[]::new)));
        }

        if (!GUARDS_DEFAULT.equals(config.guards))
        {
            JsonObjectBuilder guards = Json.createObjectBuilder();
            config.guards.forEach(v -> guards.add(v.name, guard.adaptToJson(v)));
            object.add(GUARDS_NAME, guards);
        }

        if (!VAULTS_DEFAULT.equals(config.vaults))
        {
            JsonObjectBuilder vaults = Json.createObjectBuilder();
            config.vaults.forEach(v -> vaults.add(v.name, vault.adaptToJson(v)));
            object.add(VAULTS_NAME, vaults);
        }

        if (!CATALOGS_DEFAULT.equals(config.catalogs))
        {
            JsonObjectBuilder catalogs = Json.createObjectBuilder();
            config.catalogs.forEach(s -> catalogs.add(s.name, catalog.adaptToJson(s)));
            object.add(CATALOGS_NAME, catalogs);
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
        JsonObject object)
    {
        NamespaceConfigBuilder<NamespaceConfig> namespace = NamespaceConfig.builder();

        namespace.name(object.getString(NAME_NAME));

        if (object.containsKey(TELEMETRY_NAME))
        {
            namespace.telemetry(telemetry.adaptFromJson(object.getJsonObject(TELEMETRY_NAME)));
        }

        if (object.containsKey(BINDINGS_NAME))
        {
            namespace.bindings(Arrays.asList(binding.adaptFromJson(object.getJsonObject(BINDINGS_NAME))));
        }

        if (object.containsKey(GUARDS_NAME))
        {
            object.getJsonObject(GUARDS_NAME).entrySet().stream()
                .map(e -> guard.adaptFromJson(e.getKey(), e.getValue().asJsonObject()))
                .forEach(namespace::guard);
        }

        if (object.containsKey(VAULTS_NAME))
        {
            object.getJsonObject(VAULTS_NAME).entrySet().stream()
                .map(e -> vault.adaptFromJson(e.getKey(), e.getValue().asJsonObject()))
                .forEach(namespace::vault);
        }

        if (object.containsKey(CATALOGS_NAME))
        {
            object.getJsonObject(CATALOGS_NAME).entrySet().stream()
                .map(e -> catalog.adaptFromJson(e.getKey(), e.getValue().asJsonObject()))
                .forEach(namespace::catalog);
        }

        return namespace.build();
    }
}
