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
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Namespace;
import io.aklivity.zilla.runtime.engine.config.Vault;

public class NamespaceAdapter implements JsonbAdapter<Namespace, JsonObject>
{
    private static final String NAME_NAME = "name";
    private static final String VAULTS_NAME = "vaults";
    private static final String BINDINGS_NAME = "bindings";

    private static final String NAME_DEFAULT = "default";
    private static final List<Binding> BINDINGS_DEFAULT = emptyList();
    private static final List<Vault> VAULTS_DEFAULT = emptyList();

    private final VaultAdapter vault;
    private final BindingsAdapter binding;

    public NamespaceAdapter()
    {
        vault = new VaultAdapter();
        binding = new BindingsAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        Namespace namespace) throws Exception
    {
        JsonObjectBuilder object = Json.createObjectBuilder();

        if (!NAME_DEFAULT.equals(namespace.name))
        {
            object.add(NAME_NAME, namespace.name);
        }

        if (!VAULTS_DEFAULT.equals(namespace.vaults))
        {
            JsonObjectBuilder vaults = Json.createObjectBuilder();
            namespace.vaults.forEach(v -> vaults.add(v.name, vault.adaptToJson(v)));
            object.add(VAULTS_NAME, vaults);
        }

        if (!BINDINGS_DEFAULT.equals(namespace.bindings))
        {
            object.add(BINDINGS_NAME, binding.adaptToJson(namespace.bindings.toArray(Binding[]::new)));
        }

        return object.build();
    }

    @Override
    public Namespace adaptFromJson(
        JsonObject object)
    {
        String name = object.getString(NAME_NAME, NAME_DEFAULT);
        List<Binding> bindings = object.containsKey(BINDINGS_NAME)
                ? Arrays.asList(binding.adaptFromJson(object.getJsonObject(BINDINGS_NAME)))
                : BINDINGS_DEFAULT;
        List<Vault> vaults = object.containsKey(VAULTS_NAME)
                ? object.getJsonObject(VAULTS_NAME)
                    .entrySet()
                    .stream()
                    .map(e -> vault.adaptFromJson(e.getKey(), e.getValue().asJsonObject()))
                    .collect(Collectors.toList())
                : VAULTS_DEFAULT;

        return new Namespace(name, vaults, bindings);
    }
}
