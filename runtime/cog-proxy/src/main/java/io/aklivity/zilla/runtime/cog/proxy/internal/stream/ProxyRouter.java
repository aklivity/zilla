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
package io.aklivity.zilla.runtime.cog.proxy.internal.stream;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.cog.proxy.internal.config.ProxyBinding;

public final class ProxyRouter
{
    private final int typeId;
    private final Long2ObjectHashMap<ProxyBinding> bindings;

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
        ProxyBinding binding)
    {
        bindings.put(binding.routeId, binding);
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    public ProxyBinding lookup(
        long routeId)
    {
        return bindings.get(routeId);
    }
}
