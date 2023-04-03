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
package io.aklivity.zilla.runtime.binding.proxy.internal.stream;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.proxy.internal.config.ProxyBindingConfig;

public final class ProxyRouter
{
    private final int typeId;
    private final Long2ObjectHashMap<ProxyBindingConfig> bindings;

    public ProxyRouter(
        int typeId)
    {
        this.typeId = typeId;
        this.bindings = new Long2ObjectHashMap<>();
    }

    public int typeId()
    {
        return typeId;
    }

    public void attach(
        ProxyBindingConfig binding)
    {
        bindings.put(binding.id, binding);
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    public ProxyBindingConfig lookup(
        long bindingId)
    {
        return bindings.get(bindingId);
    }
}
