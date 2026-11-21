/*
 * Copyright 2021-2026 Aklivity Inc.
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

import static io.aklivity.zilla.config.engine.KindConfig.CLIENT;
import static io.aklivity.zilla.config.engine.KindConfig.SERVER;

import java.nio.channels.SocketChannel;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.config.engine.BindingConfig;
import io.aklivity.zilla.config.engine.KindConfig;
import io.aklivity.zilla.runtime.binding.tcp.internal.stream.TcpClientFactory;
import io.aklivity.zilla.runtime.binding.tcp.internal.stream.TcpServerFactory;
import io.aklivity.zilla.runtime.binding.tcp.internal.stream.TcpStreamFactory;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;

final class TcpBindingContext implements BindingContext
{
    private final Map<KindConfig, TcpStreamFactory> factories;
    private final Consumer<Runnable> dispatch;

    private final TcpUsageTracker usage;
    private final Int2ObjectHashMap<TcpBindingContext> peers;
    private final TcpSpillover spillover;
    private final IntUnaryOperator peerLoad = this::peerLoad;

    TcpBindingContext(
        TcpConfiguration config,
        EngineContext context,
        Int2ObjectHashMap<TcpBindingContext> peers)
    {
        this.usage = new TcpUsageTracker(config, context);
        this.peers = peers;
        this.spillover = new TcpSpillover(context.index(), config.rebalanceTolerance());
        Map<KindConfig, TcpStreamFactory> factories = new EnumMap<>(KindConfig.class);
        factories.put(SERVER, new TcpServerFactory(config, context, usage, this::spill));
        factories.put(CLIENT, new TcpClientFactory(config, context, usage));

        this.dispatch = context::dispatch;
        this.factories = factories;
    }

    boolean spill(
        long bindingId,
        SocketChannel channel)
    {
        final int index = spillover.select(peers.size(), usage.used(), usage.available() == 0, peerLoad);
        final TcpBindingContext peer = index != TcpSpillover.NO_PEER ? peers.get(index) : null;

        if (peer != null)
        {
            usage.rebalanced();
            peer.dispatch.accept(() -> peer.spilled(bindingId, channel));
        }

        return peer != null;
    }

    private int peerLoad(
        int index)
    {
        final TcpBindingContext peer = peers.get(index);

        return peer != null && peer.usage.available() > 0 ? peer.usage.used() : TcpSpillover.FULL;
    }

    private void spilled(
        long bindingId,
        SocketChannel channel)
    {
        final TcpServerFactory serverFactory = (TcpServerFactory) factories.get(SERVER);
        serverFactory.onSpilled(bindingId, channel);
    }

    @Override
    public BindingHandler attach(
        BindingConfig binding)
    {
        TcpStreamFactory tcpFactory = factories.get(binding.kind);
        if (tcpFactory != null)
        {
            assert TcpBinding.NAME.equals(binding.type);
            tcpFactory.attach(binding);
        }
        return tcpFactory;
    }

    @Override
    public void detach(
        BindingConfig binding)
    {
        TcpStreamFactory tcpFactory = factories.get(binding.kind);
        if (tcpFactory != null)
        {
            tcpFactory.detach(binding.id);
        }
    }
}
