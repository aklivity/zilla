/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static java.util.Collections.emptyList;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;

public class NamespaceAdapter implements JsonbAdapter<NamespaceConfig, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String NAMESPACES_NAME = "references";
    private static final String BINDINGS_NAME = "bindings";
    private static final String GUARDS_NAME = "guards";
    private static final String VAULTS_NAME = "vaults";

    private static final List<NamespaceRef> NAMESPACES_DEFAULT = emptyList();
    private static final List<BindingConfig> BINDINGS_DEFAULT = emptyList();
    private static final List<GuardConfig> GUARDS_DEFAULT = emptyList();
    private static final List<VaultConfig> VAULTS_DEFAULT = emptyList();

    private final NamspaceRefAdapter reference;
    private final BindingConfigsAdapter binding;
    private final VaultAdapter vault;
    private final GuardAdapter guard;

    public NamespaceAdapter(
        ConfigAdapterContext context)
    {
        reference = new NamspaceRefAdapter(context);
        binding = new BindingConfigsAdapter(context);
        guard = new GuardAdapter(context);
        vault = new VaultAdapter(context);
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

        if (!NAMESPACES_DEFAULT.equals(config.references))
        {
            JsonArrayBuilder references = Json.createArrayBuilder();
            config.references.forEach(r -> references.add(reference.adaptToJson(r)));
            object.add(NAMESPACES_NAME, references);
        }

        return object.build();
    }

    @Override
    public NamespaceConfig adaptFromJson(
        JsonObject object)
    {
        String name = object.getString(NAME_NAME);
        List<NamespaceRef> references = object.containsKey(NAMESPACES_NAME)
                ? object.getJsonArray(NAMESPACES_NAME)
                    .stream().map(JsonValue::asJsonObject)
                    .map(reference::adaptFromJson)
                    .collect(Collectors.toList())
                : NAMESPACES_DEFAULT;
        List<BindingConfig> bindings = object.containsKey(BINDINGS_NAME)
                ? Arrays.asList(binding.adaptFromJson(object.getJsonObject(BINDINGS_NAME)))
                : BINDINGS_DEFAULT;
        List<GuardConfig> guards = object.containsKey(GUARDS_NAME)
                ? object.getJsonObject(GUARDS_NAME)
                    .entrySet()
                    .stream()
                    .map(e -> guard.adaptFromJson(e.getKey(), e.getValue().asJsonObject()))
                    .collect(Collectors.toList())
                : GUARDS_DEFAULT;
        List<VaultConfig> vaults = object.containsKey(VAULTS_NAME)
                ? object.getJsonObject(VAULTS_NAME)
                    .entrySet()
                    .stream()
                    .map(e -> vault.adaptFromJson(e.getKey(), e.getValue().asJsonObject()))
                    .collect(Collectors.toList())
                : VAULTS_DEFAULT;

        return new NamespaceConfig(name, references, bindings, guards, vaults);
    }
}
