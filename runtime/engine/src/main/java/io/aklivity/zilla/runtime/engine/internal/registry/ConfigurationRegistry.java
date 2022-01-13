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
package io.aklivity.zilla.runtime.engine.internal.registry;

import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.cog.CogContext;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;

public class ConfigurationRegistry
{
    private final Function<String, CogContext> bindingsByName;
    private final Function<String, VaultContext> vaultsByName;
    private final ToIntFunction<String> supplyLabelId;
    private final LongConsumer supplyLoadEntry;

    private final Int2ObjectHashMap<NamespaceRegistry> namespacesById;

    public ConfigurationRegistry(
        Function<String, CogContext> bindingsByName,
        Function<String, VaultContext> vaultsByName,
        ToIntFunction<String> supplyLabelId,
        LongConsumer supplyLoadEntry)
    {
        this.bindingsByName = bindingsByName;
        this.vaultsByName = vaultsByName;
        this.supplyLabelId = supplyLabelId;
        this.supplyLoadEntry = supplyLoadEntry;
        this.namespacesById = new Int2ObjectHashMap<>();
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

    public VaultRegistry resolveVault(
        long vaultId)
    {
        int namespaceId = NamespacedId.namespaceId(vaultId);
        int localId = NamespacedId.localId(vaultId);

        NamespaceRegistry namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findVault(localId) : null;
    }

    public void detachAll()
    {
        namespacesById.values().forEach(n -> n.detach());
        namespacesById.clear();
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
                new NamespaceRegistry(namespace, bindingsByName, vaultsByName, supplyLabelId, supplyLoadEntry);
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
