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

import static io.aklivity.zilla.engine.drive.internal.stream.BudgetId.ownerIndex;
import static io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.ZillaTransmission.HALF_DUPLEX;

import java.nio.file.Path;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.ArrayUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetCreditor;
import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetDebitor;
import io.aklivity.zilla.engine.drive.internal.layouts.BudgetsLayout;
import io.aklivity.zilla.engine.drive.internal.stream.NamespacedId;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.ZillaExtConfiguration;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.layout.StreamsLayout;
import io.aklivity.zilla.specs.cog.internal.types.stream.FlushFW;
import io.aklivity.zilla.specs.cog.internal.types.stream.WindowFW;

public final class ZillaScope implements AutoCloseable
{
    private final WindowFW windowRO = new WindowFW();
    private final FlushFW flushRO = new FlushFW();

    private final Int2ObjectHashMap<ZillaTarget> targetsByIndex;
    private final Int2ObjectHashMap<DefaultBudgetDebitor> debitorsByIndex;

    private final ZillaExtConfiguration config;
    private final LabelManager labels;
    private final MutableDirectBuffer writeBuffer;
    private final Long2ObjectHashMap<MessageHandler> streamsById;
    private final Long2ObjectHashMap<MessageHandler> throttlesById;
    private final Long2ObjectHashMap<ZillaCorrelation> correlations;
    private final ToIntFunction<Long> lookupTargetIndex;
    private final LongSupplier supplyTimestamp;
    private final LongSupplier supplyTraceId;
    private final ZillaSource source;

    private ZillaTarget[] targets = new ZillaTarget[0];

    public ZillaScope(
        ZillaExtConfiguration config,
        LabelManager labels,
        int scopeIndex,
        ToIntFunction<Long> lookupTargetIndex,
        LongSupplier supplyTimestamp,
        LongSupplier supplyTraceId)
    {
        this.config = config;
        this.labels = labels;

        this.writeBuffer = new UnsafeBuffer(new byte[config.streamsBufferCapacity() / 8]);
        this.streamsById = new Long2ObjectHashMap<>();
        this.throttlesById = new Long2ObjectHashMap<>();
        this.correlations = new Long2ObjectHashMap<>();
        this.targetsByIndex = new Int2ObjectHashMap<>();
        this.debitorsByIndex = new Int2ObjectHashMap<>();
        this.lookupTargetIndex = lookupTargetIndex;
        this.supplyTimestamp = supplyTimestamp;
        this.supplyTraceId = supplyTraceId;
        this.source = new ZillaSource(config, scopeIndex, supplyTraceId,
                correlations::remove, this::supplySender, this::supplyTarget,
                this::doSystemFlush, streamsById, throttlesById);

        this.streamsById.put(0L, this::onSystemMessage);
        this.throttlesById.put(0L, this::onSystemMessage);
    }

    @Override
    public String toString()
    {
        return String.format("%s [%s]", getClass().getSimpleName(), source.streamsPath());
    }

    public void doRoute(
        long routeId,
        long authorization,
        ZillaServerChannel serverChannel)
    {
        source.doRoute(routeId, authorization, serverChannel);
    }

    public void doUnroute(
        long routeId,
        long authorization,
        ZillaServerChannel serverChannel)
    {
        source.doUnroute(routeId, authorization, serverChannel);
    }

    public void doConnect(
        ZillaClientChannel clientChannel,
        ZillaChannelAddress localAddress,
        ZillaChannelAddress remoteAddress,
        ChannelFuture connectFuture)
    {
        long routeId = routeId(remoteAddress);
        final int targetIndex = lookupTargetIndex.applyAsInt(routeId);
        ZillaTarget target = supplyTarget(targetIndex);
        clientChannel.setRemoteScope(targetIndex);
        clientChannel.routeId(routeId);
        target.doConnect(clientChannel, localAddress, remoteAddress, connectFuture);
    }

    public void doConnectAbort(
        ZillaClientChannel clientChannel,
        ZillaChannelAddress remoteAddress)
    {
        long routeId = routeId(remoteAddress);
        final int targetIndex = lookupTargetIndex.applyAsInt(routeId);
        ZillaTarget target = supplyTarget(targetIndex);
        target.doConnectAbort(clientChannel);
    }

    public void doAdviseOutput(
        ZillaChannel channel,
        ChannelFuture adviseFuture,
        Object value)
    {
        ZillaTarget target = supplyTarget(channel);
        target.doAdviseOutput(channel, adviseFuture, value);
    }

    public void doAdviseInput(
        ZillaChannel channel,
        ChannelFuture adviseFuture,
        Object value)
    {
        source.doAdviseInput(channel, adviseFuture, value);
    }

    public void doAbortOutput(
        ZillaChannel channel,
        ChannelFuture abortFuture)
    {
        ZillaTarget target = supplyTarget(channel);
        target.doAbortOutput(channel, abortFuture);
    }

    public void doAbortInput(
        ZillaChannel channel,
        ChannelFuture abortFuture)
    {
        source.doAbortInput(channel, abortFuture);
    }

    public void doWrite(
        ZillaChannel channel,
        MessageEvent writeRequest)
    {
        ZillaTarget target = supplyTarget(channel);
        target.doWrite(channel, writeRequest);
    }

    public void doFlush(
        ZillaChannel channel,
        ChannelFuture flushFuture)
    {
        ZillaTarget target = supplyTarget(channel);
        target.doFlush(channel, flushFuture);
    }

    public void doShutdownOutput(
        ZillaChannel channel,
        ChannelFuture shutdownFuture)
    {
        ZillaTarget target = supplyTarget(channel);
        target.doShutdownOutput(channel, shutdownFuture);
    }

    public void doClose(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        final boolean readClosed = channel.getCloseFuture().isDone() || channel.isReadClosed();

        ZillaTarget target = supplyTarget(channel);
        target.doClose(channel, handlerFuture);

        if (!readClosed && channel.getConfig().getTransmission() == HALF_DUPLEX)
        {
            final ChannelFuture abortFuture = Channels.future(channel);
            source.doAbortInput(channel, abortFuture);
            assert abortFuture.isSuccess();
        }
    }

    public void doSystemFlush(
        ZillaChannel channel,
        ChannelFuture flushFuture)
    {
        ZillaTarget target = supplyTarget(channel);
        target.doSystemFlush(channel, flushFuture);
    }

    public int process()
    {
        return source.process();
    }

    @Override
    public void close()
    {
        CloseHelper.quietClose(source);

        for (ZillaTarget target : targetsByIndex.values())
        {
            CloseHelper.quietClose(target);
        }

        for (DefaultBudgetDebitor debitor : debitorsByIndex.values())
        {
            CloseHelper.quietClose(debitor);
        }
    }

    public ZillaTarget supplySender(
        long routeId,
        long streamId)
    {
        final int targetIndex = replyToIndex(streamId);
        return supplyTarget(targetIndex);
    }

    public DefaultBudgetDebitor supplyDebitor(
        long budgetId)
    {
        final int ownerIndex = ownerIndex(budgetId);
        return debitorsByIndex.computeIfAbsent(ownerIndex, this::newDebitor);
    }

    public DefaultBudgetCreditor creditor()
    {
        return source.creditor();
    }

    private DefaultBudgetDebitor newDebitor(
        int ownerIndex)
    {
        final int watcherIndex = source.scopeIndex();
        final BudgetsLayout layout = new BudgetsLayout.Builder()
                .path(config.directory().resolve(String.format("budgets%d", ownerIndex)))
                .owner(false)
                .build();

        return new DefaultBudgetDebitor(watcherIndex, ownerIndex, layout);

    }

    private void onSystemMessage(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case WindowFW.TYPE_ID:
            final WindowFW window = windowRO.wrap(buffer, index, index + length);
            onSystemWindow(window);
            break;
        case FlushFW.TYPE_ID:
            final FlushFW flush = flushRO.wrap(buffer, index, index + length);
            onSystemFlush(flush);
            break;
        }
    }

    private void onSystemWindow(
        WindowFW window)
    {
        final long traceId = window.traceId();
        final long budgetId = window.budgetId();
        final int reserved = window.maximum();

        creditor().creditById(traceId, budgetId, reserved);
    }

    private void onSystemFlush(
        FlushFW flush)
    {
        final long traceId = flush.traceId();
        final long budgetId = flush.budgetId();

        final int ownerIndex = ownerIndex(budgetId);
        final DefaultBudgetDebitor debitor = debitorsByIndex.get(ownerIndex);
        if (debitor != null)
        {
            debitor.flush(traceId, budgetId);
        }
    }

    private void doSystemFlush(
        long traceId,
        long budgetId,
        long watchers)
    {
        for (int watcherIndex = 0; watcherIndex < Long.SIZE; watcherIndex++)
        {
            if ((watchers & (1L << watcherIndex)) != 0L)
            {
                final ZillaTarget target = supplyTarget(watcherIndex);
                target.doSystemFlush(traceId, budgetId);
            }
        }
    }

    private ZillaTarget supplyTarget(
        ZillaChannel channel)
    {
        return supplyTarget(channel.getRemoteScope());
    }

    private ZillaTarget supplyTarget(
        int targetIndex)
    {
        return targetsByIndex.computeIfAbsent(targetIndex, this::newTarget);
    }

    private ZillaTarget newTarget(
        int targetIndex)
    {
        final Path targetPath = config.directory()
                .resolve(String.format("data%d", targetIndex));

        final StreamsLayout layout = new StreamsLayout.Builder()
                .path(targetPath)
                .readonly(true)
                .build();

        final ZillaTarget target = new ZillaTarget(source.scopeIndex(), targetPath, layout, writeBuffer,
                throttlesById::put, throttlesById::remove, correlations::put,
                supplyTimestamp, supplyTraceId);

        this.targets = ArrayUtil.add(this.targets, target);

        return target;
    }

    long routeId(
        ZillaChannelAddress remoteAddress)
    {
        final int namespaceId = labels.supplyLabelId(remoteAddress.getNamespace());
        final int bindingId = labels.supplyLabelId(remoteAddress.getBinding());
        return NamespacedId.id(namespaceId, bindingId);
    }

    private static int replyToIndex(
        long streamId)
    {
        return isInitial(streamId) ? localIndex(streamId) : remoteIndex(streamId);
    }

    private static int localIndex(
        long streamId)
    {
        return (int)(streamId >> 56) & 0x7f;
    }

    private static int remoteIndex(
        long streamId)
    {
        return (int)(streamId >> 48) & 0x7f;
    }

    private static boolean isInitial(
        long streamId)
    {
        return (streamId & 0x0000_0000_0000_0001L) != 0L;
    }
}
