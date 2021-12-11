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
package io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior;

import static io.aklivity.zilla.engine.drive.test.internal.k3po.ext.types.ZillaTypeSystem.ADVISORY_CHALLENGE;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;

import java.nio.file.Path;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.agrona.CloseHelper;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.MessageHandler;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetCreditor;
import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetCreditor.BudgetFlusher;
import io.aklivity.zilla.engine.drive.internal.layouts.BudgetsLayout;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.ZillaExtConfiguration;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.layout.StreamsLayout;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.util.function.LongLongFunction;

public final class ZillaSource implements AutoCloseable
{
    private final Path streamsPath;
    private final ZillaStreamFactory streamFactory;
    private final LongSupplier supplyTraceId;
    private final ZillaPartition partition;
    private final Long2ObjectHashMap<Long2ObjectHashMap<ZillaServerChannel>> routesByIdAndAuth;
    private final DefaultBudgetCreditor creditor;

    public ZillaSource(
        ZillaExtConfiguration config,
        int scopeIndex,
        LongSupplier supplyTraceId,
        LongFunction<ZillaCorrelation> correlateEstablished,
        LongLongFunction<ZillaTarget> supplySender,
        IntFunction<ZillaTarget> supplyTarget,
        BudgetFlusher flushWatchers,
        Long2ObjectHashMap<MessageHandler> streamsById,
        Long2ObjectHashMap<MessageHandler> throttlesById)
    {
        this.streamsPath = config.directory().resolve(String.format("data%d", scopeIndex));
        this.streamFactory = new ZillaStreamFactory(supplySender, streamsById::remove);
        this.routesByIdAndAuth = new Long2ObjectHashMap<>();

        BudgetsLayout budgets = new BudgetsLayout.Builder()
                .path(config.directory().resolve(String.format("budgets%d", scopeIndex)))
                .capacity(config.budgetsBufferCapacity())
                .owner(true)
                .build();

        StreamsLayout streams = new StreamsLayout.Builder()
                .path(streamsPath)
                .streamsCapacity(config.streamsBufferCapacity())
                .readonly(false)
                .build();

        this.supplyTraceId = supplyTraceId;
        this.partition = new ZillaPartition(streamsPath, scopeIndex, streams, this::lookupRoute,
                streamsById::get, streamsById::put, throttlesById::get,
                streamFactory, correlateEstablished, supplySender, supplyTarget);
        this.creditor = new DefaultBudgetCreditor(scopeIndex, budgets, flushWatchers);
    }

    @Override
    public String toString()
    {
        return String.format("%s [%s]", getClass().getSimpleName(), streamsPath);
    }

    public void doRoute(
        long routeId,
        long authorization,
        ZillaServerChannel serverChannel)
    {
        routesByAuth(routeId).put(authorization, serverChannel);
    }

    public void doUnroute(
        long routeId,
        long authorization,
        ZillaServerChannel serverChannel)
    {
        Long2ObjectHashMap<ZillaServerChannel> channels = routesByIdAndAuth.get(routeId);
        if (channels != null && channels.remove(authorization) != null && channels.isEmpty())
        {
            routesByIdAndAuth.remove(routeId);
        }
    }

    public void doAdviseInput(
        ZillaChannel channel,
        ChannelFuture adviseFuture,
        Object value)
    {
        if (value == ADVISORY_CHALLENGE)
        {
            final long traceId = supplyTraceId.getAsLong();

            streamFactory.doChallenge(channel, traceId);

            adviseFuture.setSuccess();
        }
        else
        {
            adviseFuture.setFailure(new ChannelException("unexpected: " + value));
        }
    }

    public void doAbortInput(
        ZillaChannel channel,
        ChannelFuture abortFuture)
    {
        boolean isClientChannel = channel.getParent() == null;
        boolean isHalfDuplex = channel.getConfig().getTransmission() == ZillaTransmission.HALF_DUPLEX;
        ChannelFuture beginFuture = isClientChannel && isHalfDuplex ? channel.beginOutputFuture() : channel.beginInputFuture();
        if (beginFuture.isSuccess())
        {
            doAbortInputAfterBegin(channel, abortFuture);
        }
        else
        {
            beginFuture.addListener(new ChannelFutureListener()
            {
                @Override
                public void operationComplete(
                    ChannelFuture future) throws Exception
                {
                    if (future.isSuccess())
                    {
                        doAbortInputAfterBegin(channel, abortFuture);
                    }
                    else
                    {
                        abortFuture.setFailure(future.getCause());
                    }
                }
            });
        }
    }

    private void doAbortInputAfterBegin(
        ZillaChannel channel,
        ChannelFuture abortFuture)
    {
        final long traceId = supplyTraceId.getAsLong();

        streamFactory.doReset(channel, traceId);
        partition.doSystemWindow(channel, traceId);

        abortFuture.setSuccess();
        if (channel.setReadAborted())
        {
            if (channel.setReadClosed())
            {
                fireChannelDisconnected(channel);
                fireChannelUnbound(channel);
                fireChannelClosed(channel);
            }
        }
    }

    public int process()
    {
        return partition.process();
    }

    @Override
    public void close()
    {
        CloseHelper.quietClose(creditor);

        partition.close();
    }

    Path streamsPath()
    {
        return streamsPath;
    }

    int scopeIndex()
    {
        return partition.scopeIndex();
    }

    DefaultBudgetCreditor creditor()
    {
        return creditor;
    }

    private ZillaServerChannel lookupRoute(
        long routeId,
        long authorization)
    {
        Long2ObjectHashMap<ZillaServerChannel> routesByAuth = routesByAuth(routeId);
        return routesByAuth.get(authorization);
    }

    private Long2ObjectHashMap<ZillaServerChannel> routesByAuth(
        long routeId)
    {
        return routesByIdAndAuth.computeIfAbsent(routeId, this::newRoutesByAuth);
    }

    private Long2ObjectHashMap<ZillaServerChannel> newRoutesByAuth(
        long routeId)
    {
        return new Long2ObjectHashMap<ZillaServerChannel>();
    }
}
