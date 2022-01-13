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
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;

public class NamespaceRegistry
{
    private final NamespaceConfig namespace;
    private final Function<String, CogContext> bindingsByName;
    private final Function<String, VaultContext> vaultsByName;
    private final ToIntFunction<String> supplyLabelId;
    private final LongConsumer supplyLoadEntry;
    private final int namespaceId;
    private final Int2ObjectHashMap<BindingRegistry> bindingsById;
    private final Int2ObjectHashMap<VaultRegistry> vaultsById;

    public NamespaceRegistry(
        NamespaceConfig namespace,
        Function<String, CogContext> bindingsByName,
        Function<String, VaultContext> vaultsByName,
        ToIntFunction<String> supplyLabelId,
        LongConsumer supplyLoadEntry)
    {
        this.namespace = namespace;
        this.bindingsByName = bindingsByName;
        this.vaultsByName = vaultsByName;
        this.supplyLabelId = supplyLabelId;
        this.supplyLoadEntry = supplyLoadEntry;
        this.namespaceId = supplyLabelId.applyAsInt(namespace.name);
        this.bindingsById = new Int2ObjectHashMap<>();
        this.vaultsById = new Int2ObjectHashMap<>();
    }

    public int namespaceId()
    {
        return namespaceId;
    }

    public void attach()
    {
        namespace.vaults.forEach(this::attachVault);
        namespace.bindings.forEach(this::attachBinding);
    }

    public void detach()
    {
        namespace.vaults.forEach(this::detachVault);
        namespace.bindings.forEach(this::detachBinding);
    }

    private void attachBinding(
        BindingConfig binding)
    {
        CogContext context = bindingsByName.apply(binding.type);
        assert context != null : "Missing cog kind: " + binding.type;

        int bindingId = supplyLabelId.applyAsInt(binding.entry);
        BindingRegistry registry = new BindingRegistry(binding, context);
        bindingsById.put(bindingId, registry);
        registry.attach();
        supplyLoadEntry.accept(binding.id);
    }

    private void detachBinding(
        BindingConfig binding)
    {
        int bindingId = supplyLabelId.applyAsInt(binding.entry);
        BindingRegistry context = bindingsById.remove(bindingId);
        if (context != null)
        {
            context.detach();
        }
    }

    private void attachVault(
        VaultConfig vault)
    {
        VaultContext context = vaultsByName.apply(vault.type);
        assert context != null : "Missing vault kind: " + vault.type;

        int vaultId = supplyLabelId.applyAsInt(vault.name);
        VaultRegistry registry = new VaultRegistry(vault, context);
        vaultsById.put(vaultId, registry);
        registry.attach();
    }

    private void detachVault(
        VaultConfig vault)
    {
        int vaultId = supplyLabelId.applyAsInt(vault.name);
        VaultRegistry context = vaultsById.remove(vaultId);
        if (context != null)
        {
            context.detach();
        }
    }

    BindingRegistry findBinding(
        int bindingId)
    {
        return bindingsById.get(bindingId);
    }

    VaultRegistry findVault(
        int vaultId)
    {
        return vaultsById.get(vaultId);
    }
}
