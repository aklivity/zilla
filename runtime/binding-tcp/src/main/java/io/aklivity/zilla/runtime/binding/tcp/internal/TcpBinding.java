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
package io.aklivity.zilla.runtime.binding.tcp.internal;

import static io.aklivity.zilla.runtime.engine.config.KindConfig.CLIENT;
import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static io.aklivity.zilla.runtime.engine.config.StreamType.PROXY;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.aklivity.zilla.runtime.binding.tcp.internal.config.TcpServerBindingConfig;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.StreamType;

public final class TcpBinding implements Binding
{
    public static final String NAME = "tcp";

    public static final int WRITE_SPIN_COUNT = 16;

    private final TcpConfiguration config;
    private final ConcurrentMap<Long, TcpServerBindingConfig> servers;

    TcpBinding(
        TcpConfiguration config)
    {
        this.config = config;
        this.servers = new ConcurrentHashMap<>();
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
        return new TcpBindingContext(config, context, this::supplyServer);
    }

    @Override
    public StreamType originType(
        KindConfig kind)
    {
        return kind == SERVER ? PROXY : null;
    }

    @Override
    public StreamType routedType(
        KindConfig kind)
    {
        return kind == CLIENT ? PROXY : null;
    }

    private TcpServerBindingConfig supplyServer(
        long bindingId)
    {
        return servers.computeIfAbsent(bindingId, TcpServerBindingConfig::new);
    }
}
