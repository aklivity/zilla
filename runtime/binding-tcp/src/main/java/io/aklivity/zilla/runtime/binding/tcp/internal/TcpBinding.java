/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import java.net.URL;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.EngineController;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingController;

public final class TcpBinding implements Binding
{
    public static final String NAME = "tcp";

    public static final int WRITE_SPIN_COUNT = 16;

    private final TcpConfiguration config;
    private final Int2ObjectHashMap<TcpBindingContext> contexts;

    TcpBinding(
        TcpConfiguration config)
    {
        this.config = config;
        this.contexts = new Int2ObjectHashMap<>();
    }

    @Override
    public String name()
    {
        return TcpBinding.NAME;
    }

    @Override
    public URL type()
    {
        return getClass().getResource("schema/tcp.schema.patch.json");
    }

    @Override
    public BindingContext supply(
        EngineContext context)
    {
        TcpBindingContext tcpContext = new TcpBindingContext(config, context);
        contexts.put(context.index(), tcpContext);
        return tcpContext;
    }

    @Override
    public BindingController supply(
        EngineController controller)
    {
        return new TcpBindingController(config, controller, contexts);
    }
}
