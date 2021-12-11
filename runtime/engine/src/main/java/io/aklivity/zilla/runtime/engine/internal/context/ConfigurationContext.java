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
import io.aklivity.zilla.runtime.engine.config.Namespace;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public class ConfigurationContext
{
    private final Function<String, Axle> elektronsByName;
    private final ToIntFunction<String> supplyLabelId;
    private final LongConsumer supplyLoadEntry;

    private final Int2ObjectHashMap<NamespaceContext> namespacesById;

    public ConfigurationContext(
        Function<String, Axle> elektronsByName,
        ToIntFunction<String> supplyLabelId,
        LongConsumer supplyLoadEntry)
    {
        this.elektronsByName = elektronsByName;
        this.supplyLabelId = supplyLabelId;
        this.supplyLoadEntry = supplyLoadEntry;
        this.namespacesById = new Int2ObjectHashMap<>();
    }

    public NamespaceTask attach(
        Namespace namespace)
    {
        return new NamespaceTask(namespace, this::attachNamespace);
    }

    public NamespaceTask detach(
        Namespace namespace)
    {
        return new NamespaceTask(namespace, this::detachNamespace);
    }

    public BindingContext resolveBinding(
        long bindingId)
    {
        int namespaceId = NamespacedId.namespaceId(bindingId);
        int localId = NamespacedId.localId(bindingId);

        NamespaceContext namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findBinding(localId) : null;
    }

    public VaultContext resolveVault(
        long vaultId)
    {
        int namespaceId = NamespacedId.namespaceId(vaultId);
        int localId = NamespacedId.localId(vaultId);

        NamespaceContext namespace = findNamespace(namespaceId);
        return namespace != null ? namespace.findVault(localId) : null;
    }

    public void detachAll()
    {
        namespacesById.values().forEach(n -> n.detach());
        namespacesById.clear();
    }

    private NamespaceContext findNamespace(
        int namespaceId)
    {
        return namespacesById.get(namespaceId);
    }

    private void attachNamespace(
        Namespace namespace)
    {
        NamespaceContext context = new NamespaceContext(namespace, elektronsByName, supplyLabelId, supplyLoadEntry);
        namespacesById.put(context.namespaceId(), context);
        context.attach();
    }

    protected void detachNamespace(
        Namespace namespace)
    {
        int namespaceId = supplyLabelId.applyAsInt(namespace.name);
        NamespaceContext context = namespacesById.remove(namespaceId);
        context.detach();
    }
}
