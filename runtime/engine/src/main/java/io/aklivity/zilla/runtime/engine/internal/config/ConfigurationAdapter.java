/*
 * Copyright 2021-2021 Aklivity Inc.
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

import java.util.List;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Vault;

public class ConfigurationAdapter implements JsonbAdapter<Configuration, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String BINDINGS_NAME = "bindings";
    private static final String NAMESPACES_NAME = "namespaces";
    private static final String VAULTS_NAME = "vaults";

    private static final String NAME_DEFAULT = "default";
    private static final List<NamespaceRef> NAMESPACES_DEFAULT = emptyList();
    private static final List<Binding> BINDINGS_DEFAULT = emptyList();
    private static final List<Vault> VAULTS_DEFAULT = emptyList();

    private final NamspaceRefAdapter namespace;
    private final VaultAdapter vault;
    private final BindingAdapter binding;

    public ConfigurationAdapter()
    {
        namespace = new NamspaceRefAdapter();
        vault = new VaultAdapter();
        binding = new BindingAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        Configuration root) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (!NAME_DEFAULT.equals(root.name))
        {
            object.add(NAME_NAME, root.name);
        }

        if (!VAULTS_DEFAULT.equals(root.vaults))
        {
            JsonArrayBuilder vaults = Json.createArrayBuilder();
            root.vaults.forEach(b -> vaults.add(vault.adaptToJson(b)));
            object.add(VAULTS_NAME, vaults);
        }

        if (!BINDINGS_DEFAULT.equals(root.bindings))
        {
            JsonArrayBuilder bindings = Json.createArrayBuilder();
            root.bindings.forEach(b -> bindings.add(binding.adaptToJson(b)));
            object.add(BINDINGS_NAME, bindings);
        }

        if (!NAMESPACES_DEFAULT.equals(root.namespaces))
        {
            JsonArrayBuilder references = Json.createArrayBuilder();
            root.namespaces.forEach(r -> references.add(namespace.adaptToJson(r)));
            object.add(NAMESPACES_NAME, references);
        }

        return object.build();
    }

    @Override
    public Configuration adaptFromJson(
        JsonObject object)
    {
        String name = object.containsKey(NAME_NAME)
                ? object.getString(NAME_NAME)
                : NAME_DEFAULT;
        List<NamespaceRef> namespaces = object.containsKey(NAMESPACES_NAME)
                ? object.getJsonArray(NAMESPACES_NAME)
                    .stream().map(JsonValue::asJsonObject)
                    .map(namespace::adaptFromJson)
                    .collect(Collectors.toList())
                : NAMESPACES_DEFAULT;
        List<Binding> bindings = object.containsKey(BINDINGS_NAME)
            ? object.getJsonArray(BINDINGS_NAME)
                .stream().map(JsonValue::asJsonObject)
                .map(binding::adaptFromJson)
                .collect(Collectors.toList())
            : BINDINGS_DEFAULT;
        List<Vault> vaults = object.containsKey(VAULTS_NAME)
                ? object.getJsonArray(VAULTS_NAME)
                    .stream().map(JsonValue::asJsonObject)
                    .map(vault::adaptFromJson)
                    .collect(Collectors.toList())
                : VAULTS_DEFAULT;

        return new Configuration(name, namespaces, vaults, bindings);
    }
}
