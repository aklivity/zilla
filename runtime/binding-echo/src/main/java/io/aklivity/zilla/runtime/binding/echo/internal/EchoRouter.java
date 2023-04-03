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
package io.aklivity.zilla.runtime.binding.echo.internal;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;

public final class EchoRouter
{
    private final Long2ObjectHashMap<BindingConfig> bindings;

    EchoRouter()
    {
        this.bindings = new Long2ObjectHashMap<>();
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, binding);
    }

    public BindingConfig resolve(
        long bindingId,
        long authorization)
    {
        return bindings.get(bindingId);
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public String toString()
    {
        return String.format("%s %s", getClass().getSimpleName(), bindings);
    }
}
