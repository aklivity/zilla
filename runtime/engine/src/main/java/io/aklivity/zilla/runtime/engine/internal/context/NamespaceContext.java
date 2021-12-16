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
package io.aklivity.zilla.runtime.engine.internal.context;

import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.ToIntFunction;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.cog.Axle;
import io.aklivity.zilla.runtime.engine.config.Binding;
import io.aklivity.zilla.runtime.engine.config.Namespace;
import io.aklivity.zilla.runtime.engine.config.Vault;

public class NamespaceContext
{
    private final Namespace namespace;
    private final Function<String, Axle> lookupAxle;
    private final ToIntFunction<String> supplyLabelId;
    private final LongConsumer supplyLoadEntry;
    private final int namespaceId;
    private final Int2ObjectHashMap<BindingContext> bindingsById;
    private final Int2ObjectHashMap<VaultContext> vaultsById;

    public NamespaceContext(
        Namespace namespace,
        Function<String, Axle> lookupAxle,
        ToIntFunction<String> supplyLabelId,
        LongConsumer supplyLoadEntry)
    {
        this.namespace = namespace;
        this.lookupAxle = lookupAxle;
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
        Binding binding)
    {
        Axle axle = lookupAxle.apply(binding.type);
        if (axle != null)
        {
            int bindingId = supplyLabelId.applyAsInt(binding.entry);
            BindingContext context = new BindingContext(binding, axle);
            bindingsById.put(bindingId, context);
            context.attach();
            supplyLoadEntry.accept(binding.id);
        }
    }

    private void detachBinding(
        Binding binding)
    {
        int bindingId = supplyLabelId.applyAsInt(binding.entry);
        BindingContext context = bindingsById.remove(bindingId);
        if (context != null)
        {
            context.detach();
        }
    }

    private void attachVault(
        Vault vault)
    {
        Axle axle = lookupAxle.apply(vault.type);
        if (axle != null)
        {
            int vaultId = supplyLabelId.applyAsInt(vault.name);
            VaultContext context = new VaultContext(vault, axle);
            vaultsById.put(vaultId, context);
            context.attach();
        }
    }

    private void detachVault(
        Vault vault)
    {
        int vaultId = supplyLabelId.applyAsInt(vault.name);
        VaultContext context = vaultsById.remove(vaultId);
        if (context != null)
        {
            context.detach();
        }
    }

    BindingContext findBinding(
        int bindingId)
    {
        return bindingsById.get(bindingId);
    }

    VaultContext findVault(
        int vaultId)
    {
        return vaultsById.get(vaultId);
    }
}
