/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.stream;

import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.AguiA2aConfiguration;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.config.AguiA2aBindingConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class AguiA2aProxyFactory implements BindingHandler
{
    private final AguiA2aConfiguration config;
    private final EngineContext context;
    private final Long2ObjectHashMap<AguiA2aBindingConfig> bindings;

    public AguiA2aProxyFactory(
        AguiA2aConfiguration config,
        EngineContext context)
    {
        this.config = config;
        this.context = context;
        this.bindings = new Long2ObjectHashMap<>();
    }

    public void attach(
        BindingConfig binding)
    {
        bindings.put(binding.id, new AguiA2aBindingConfig(binding));
    }

    public void detach(
        long bindingId)
    {
        bindings.remove(bindingId);
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        return null;
    }
}
