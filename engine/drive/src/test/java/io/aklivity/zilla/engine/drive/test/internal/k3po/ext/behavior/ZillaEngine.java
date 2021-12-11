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

import static io.aklivity.zilla.engine.drive.test.internal.k3po.ext.behavior.ZillaTransmission.SIMPLEX;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.agrona.CloseHelper;
import org.agrona.collections.ArrayUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.ExternalResourceReleasable;

import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetCreditor;
import io.aklivity.zilla.engine.drive.internal.budget.DefaultBudgetDebitor;
import io.aklivity.zilla.engine.drive.test.internal.k3po.ext.ZillaExtConfiguration;

public final class ZillaEngine implements Runnable, ExternalResourceReleasable
{
    private static final long MAX_PARK_NS = MILLISECONDS.toNanos(100L);
    private static final long MIN_PARK_NS = MILLISECONDS.toNanos(1L);
    private static final int MAX_YIELDS = 30;
    private static final int MAX_SPINS = 20;

    private final ZillaExtConfiguration config;
    private final Deque<Runnable> taskQueue;
    private final AtomicLong traceIds;
    private final Int2ObjectHashMap<ZillaScope> scopesByIndex;
    private final Map<Long, Integer> scopeIndexByRouteId;
    private final LabelManager labels;

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final AtomicBoolean shutdown = new AtomicBoolean();

    private ZillaScope[] scopes;
    private final AtomicReference<Thread> thread;

    ZillaEngine(
        ZillaExtConfiguration config)
    {
        this.config = config;
        this.scopesByIndex = new Int2ObjectHashMap<>();
        this.scopeIndexByRouteId = new ConcurrentHashMap<>();
        this.taskQueue = new ConcurrentLinkedDeque<>();
        this.traceIds = new AtomicLong(Long.MIN_VALUE); // negative
        this.scopes = new ZillaScope[0];
        this.labels = new LabelManager(config.directory());
        this.thread = new AtomicReference<>();
    }

    public void bind(
        ZillaServerChannel serverChannel,
        ZillaChannelAddress localAddress,
        ChannelFuture bindFuture)
    {
        submitTask(new BindServerTask(serverChannel, localAddress, bindFuture), true);
    }

    public void unbind(
        ZillaServerChannel serverChannel,
        ChannelFuture unbindFuture)
    {
        submitTask(new UnbindServerTask(serverChannel, unbindFuture), true);
    }

    public void close(
        ZillaServerChannel serverChannel)
    {
        submitTask(new CloseServerTask(serverChannel), true);
    }

    public void connect(
        ZillaClientChannel channel,
        ZillaChannelAddress remoteAddress,
        ChannelFuture connectFuture)
    {
        submitTask(new ConnectClientTask(channel, remoteAddress, connectFuture));
    }

    public void adviseOutput(
        ZillaChannel channel,
        ChannelFuture handlerFuture,
        Object value)
    {
        submitTask(new AdviseOutputTask(channel, handlerFuture, value));
    }

    public void adviseInput(
        ZillaChannel channel,
        ChannelFuture handlerFuture,
        Object value)
    {
        submitTask(new AdviseInputTask(channel, handlerFuture, value));
    }

    public void abortOutput(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        submitTask(new AbortOutputTask(channel, handlerFuture));
    }

    public void abortInput(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        submitTask(new AbortInputTask(channel, handlerFuture));
    }

    public void write(
        MessageEvent writeRequest)
    {
        submitTask(new WriteTask(writeRequest));
    }

    public void flush(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        submitTask(new FlushTask(channel, handlerFuture));
    }

    public void shutdownOutput(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        submitTask(new ShutdownOutputTask(channel, handlerFuture));
    }

    public void close(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        submitTask(new CloseTask(channel, handlerFuture));
    }

    public void systemFlush(
        ZillaChannel channel,
        ChannelFuture handlerFuture)
    {
        submitTask(new SystemFlushTask(channel, handlerFuture));
    }

    @Override
    public void run()
    {
        final IdleStrategy idleStrategy = new BackoffIdleStrategy(MAX_SPINS, MAX_YIELDS, MIN_PARK_NS, MAX_PARK_NS);

        if (!thread.compareAndSet(null, Thread.currentThread()))
        {
            return;
        }

        while (!shutdown.get())
        {
            int workCount = 0;

            workCount += executeTasks();
            workCount += readMessages();

            idleStrategy.idle(workCount);
        }

        // ensure task queue is drained
        // so that all channels are closed
        executeTasks();

        shutdownLatch.countDown();
    }

    public void shutdown()
    {
        if (shutdown.compareAndSet(false, true))
        {
            try
            {
                shutdownLatch.await();

                for (int i = 0; i < scopes.length; i++)
                {
                    CloseHelper.quietClose(scopes[i]);
                }
            }
            catch (InterruptedException ex)
            {
                currentThread().interrupt();
            }
        }
    }

    @Override
    public void releaseExternalResources()
    {
        shutdown();
    }

    DefaultBudgetCreditor supplyCreditor(
        ZillaChannel channel)
    {
        int scopeIndex = channel.getLocalScope();
        ZillaScope scope = supplyScope(scopeIndex);
        return scope.creditor();
    }

    DefaultBudgetDebitor supplyDebitor(
        ZillaChannel channel,
        long debitorId)
    {
        int scopeIndex = channel.getLocalScope();
        ZillaScope scope = supplyScope(scopeIndex);
        return scope.supplyDebitor(debitorId);
    }

    private ZillaScope supplyScope(
        int scopeIndex)
    {
        return scopesByIndex.computeIfAbsent(scopeIndex, this::newScope);
    }

    private int executeTasks()
    {
        int workCount = 0;

        Runnable task;
        while ((task = taskQueue.poll()) != null)
        {
            task.run();
            workCount++;
        }

        return workCount;
    }

    private int readMessages()
    {
        int workCount = 0;

        for (int i = 0; i < scopes.length; i++)
        {
            workCount += scopes[i].process();
        }

        return workCount;
    }

    private ZillaScope newScope(
        int scopeIndex)
    {
        ZillaScope scope = new ZillaScope(config, labels, scopeIndex, this::lookupTargetIndex,
                System::nanoTime, traceIds::incrementAndGet);
        this.scopes = ArrayUtil.add(this.scopes, scope);
        return scope;
    }

    private void submitTask(
        Runnable task)
    {
        submitTask(task, false);
    }

    private void submitTask(
        Runnable task,
        boolean immediateIfAligned)
    {
        if (immediateIfAligned && thread.get() == Thread.currentThread())
        {
            task.run();
        }
        else
        {
            taskQueue.offer(task);
        }
    }

    private int lookupTargetIndex(
        long routeId)
    {
        return scopeIndexByRouteId.getOrDefault(routeId, 0);
    }

    private final class BindServerTask implements Runnable
    {
        private final ZillaServerChannel serverChannel;
        private final ZillaChannelAddress localAddress;
        private final ChannelFuture bindFuture;

        private BindServerTask(
            ZillaServerChannel serverChannel,
            ZillaChannelAddress localAddress,
            ChannelFuture bindFuture)
        {
            this.serverChannel = serverChannel;
            this.localAddress = localAddress;
            this.bindFuture = bindFuture;
        }

        @Override
        public void run()
        {
            try
            {
                ZillaEngine engine = serverChannel.engine;

                int scopeIndex = serverChannel.getLocalScope();
                ZillaScope scope = engine.supplyScope(scopeIndex);

                long routeId = scope.routeId(localAddress);
                long authorization = localAddress.getAuthorization();
                scope.doRoute(routeId, authorization, serverChannel);

                scopeIndexByRouteId.put(routeId, scopeIndex);

                serverChannel.setLocalAddress(localAddress);
                serverChannel.setBound();

                fireChannelBound(serverChannel, localAddress);
                bindFuture.setSuccess();
            }
            catch (Exception ex)
            {
                bindFuture.setFailure(ex);
            }
        }
    }

    private final class UnbindServerTask implements Runnable
    {
        private final ZillaServerChannel serverChannel;
        private final ChannelFuture unbindFuture;

        private UnbindServerTask(
            ZillaServerChannel serverChannel,
            ChannelFuture unbindFuture)
        {
            this.serverChannel = serverChannel;
            this.unbindFuture = unbindFuture;
        }

        @Override
        public void run()
        {
            try
            {
                ZillaEngine engine = serverChannel.engine;
                ZillaChannelAddress localAddress = serverChannel.getLocalAddress();

                int scopeIndex = serverChannel.getLocalScope();
                ZillaScope scope = engine.supplyScope(scopeIndex);

                long routeId = scope.routeId(localAddress);
                long authorization = localAddress.getAuthorization();
                scope.doUnroute(routeId, authorization, serverChannel);

                serverChannel.setLocalAddress(null);
                fireChannelUnbound(serverChannel);
                unbindFuture.setSuccess();
            }
            catch (Exception ex)
            {
                unbindFuture.setFailure(ex);
            }
        }
    }

    private final class CloseServerTask implements Runnable
    {
        private final ZillaServerChannel serverChannel;

        private CloseServerTask(
            ZillaServerChannel serverChannel)
        {
            this.serverChannel = serverChannel;
        }

        @Override
        public void run()
        {
            try
            {
                ZillaEngine engine = serverChannel.engine;
                ZillaChannelAddress localAddress = serverChannel.getLocalAddress();

                if (localAddress != null)
                {
                    int scopeIndex = serverChannel.getLocalScope();
                    ZillaScope scope = engine.supplyScope(scopeIndex);

                    long routeId = scope.routeId(localAddress);
                    long authorization = localAddress.getAuthorization();
                    scope.doUnroute(routeId, authorization, serverChannel);

                    serverChannel.setLocalAddress(null);
                    fireChannelUnbound(serverChannel);
                }

                serverChannel.setClosed();
            }
            catch (ChannelException ex)
            {
                fireExceptionCaught(serverChannel, ex);
            }
        }
    }

    private final class ConnectClientTask implements Runnable
    {
        private final ZillaClientChannel clientChannel;
        private final ZillaChannelAddress remoteAddress;
        private final ChannelFuture connectFuture;

        private ConnectClientTask(
            ZillaClientChannel clientChannel,
            ZillaChannelAddress remoteAddress,
            ChannelFuture connectFuture)
        {
            this.clientChannel = clientChannel;
            this.remoteAddress = remoteAddress;
            this.connectFuture = connectFuture;
        }

        @Override
        public void run()
        {
            final ZillaChannelAddress localAddress = remoteAddress.newEphemeralAddress();

            if (!clientChannel.isBound())
            {
                clientChannel.setLocalAddress(localAddress);
                clientChannel.setBound();
                fireChannelBound(clientChannel, localAddress);
            }

            try
            {
                ZillaEngine engine = clientChannel.engine;
                int scopeIndex = clientChannel.getLocalScope();

                final ZillaChannelConfig clientConfig = clientChannel.getConfig();
                if (clientConfig.getTransmission() == SIMPLEX)
                {
                    clientChannel.setReadClosed();
                }

                ZillaScope scope = engine.supplyScope(scopeIndex);
                scope.doConnect(clientChannel, localAddress, remoteAddress, connectFuture);

                connectFuture.addListener(new ChannelFutureListener()
                {
                    @Override
                    public void operationComplete(
                        ChannelFuture future) throws Exception
                    {
                        if (future.isCancelled())
                        {
                            submitTask(new ConnectAbortTask(clientChannel, remoteAddress));
                        }
                    }
                });
            }
            catch (Exception ex)
            {
                connectFuture.setFailure(ex);
            }
        }

        private final class ConnectAbortTask implements Runnable
        {
            private final ZillaClientChannel clientChannel;
            private final ZillaChannelAddress remoteAddress;

            private ConnectAbortTask(
                ZillaClientChannel clientChannel,
                ZillaChannelAddress remoteAddress)
            {
                this.clientChannel = clientChannel;
                this.remoteAddress = remoteAddress;
            }

            @Override
            public void run()
            {
                ZillaEngine engine = clientChannel.engine;
                int scopeIndex = clientChannel.getLocalScope();

                ZillaScope scope = engine.supplyScope(scopeIndex);
                scope.doConnectAbort(clientChannel, remoteAddress);
            }
        }
    }

    private final class AdviseOutputTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture handlerFuture;
        private final Object value;

        private AdviseOutputTask(
            ZillaChannel channel,
            ChannelFuture handlerFuture,
            Object value)
        {
            this.channel = channel;
            this.handlerFuture = handlerFuture;
            this.value = value;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isWriteClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();  // ??

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doAdviseOutput(channel, handlerFuture, value);
                }
            }
            catch (Exception ex)
            {
                handlerFuture.setFailure(ex);
            }
        }
    }

    private final class AdviseInputTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture handlerFuture;
        private final Object value;

        private AdviseInputTask(
            ZillaChannel channel,
            ChannelFuture handlerFuture,
            Object value)
        {
            this.channel = channel;
            this.handlerFuture = handlerFuture;
            this.value = value;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isReadClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();
                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doAdviseInput(channel, handlerFuture, value);
                }
            }
            catch (Exception ex)
            {
                handlerFuture.setFailure(ex);
            }
        }
    }

    private final class AbortOutputTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture handlerFuture;

        private AbortOutputTask(
            ZillaChannel channel,
            ChannelFuture handlerFuture)
        {
            this.channel = channel;
            this.handlerFuture = handlerFuture;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isWriteClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();  // ??

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doAbortOutput(channel, handlerFuture);
                }
            }
            catch (Exception ex)
            {
                handlerFuture.setFailure(ex);
            }
        }
    }

    private final class AbortInputTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture handlerFuture;

        private AbortInputTask(
            ZillaChannel channel,
            ChannelFuture handlerFuture)
        {
            this.channel = channel;
            this.handlerFuture = handlerFuture;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isReadClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();
                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doAbortInput(channel, handlerFuture);
                }
            }
            catch (Exception ex)
            {
                handlerFuture.setFailure(ex);
            }
        }
    }

    private final class WriteTask implements Runnable
    {
        private final MessageEvent writeRequest;

        private WriteTask(
            MessageEvent writeRequest)
        {
            this.writeRequest = writeRequest;
        }

        @Override
        public void run()
        {
            try
            {
                ZillaChannel channel = (ZillaChannel) writeRequest.getChannel();
                if (!channel.isWriteClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doWrite(channel, writeRequest);
                }
            }
            catch (Exception ex)
            {
                ChannelFuture writeFuture = writeRequest.getFuture();
                writeFuture.setFailure(ex);
            }
        }
    }

    private final class FlushTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture flushFuture;

        private FlushTask(
            ZillaChannel channel,
            ChannelFuture future)
        {
            this.channel = channel;
            this.flushFuture = future;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isWriteClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doFlush(channel, flushFuture);
                }
            }
            catch (Exception ex)
            {
                flushFuture.setFailure(ex);
            }
        }
    }

    private final class ShutdownOutputTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture handlerFuture;

        private ShutdownOutputTask(
            ZillaChannel channel,
            ChannelFuture handlerFuture)
        {
            this.channel = channel;
            this.handlerFuture = handlerFuture;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isWriteClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doShutdownOutput(channel, handlerFuture);
                }
            }
            catch (Exception ex)
            {
                handlerFuture.setFailure(ex);
            }
        }
    }

    private final class CloseTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture handlerFuture;

        private CloseTask(
            ZillaChannel channel,
            ChannelFuture handlerFuture)
        {
            this.channel = channel;
            this.handlerFuture = handlerFuture;
        }

        @Override
        public void run()
        {
            try
            {
                ZillaEngine engine = channel.engine;
                ZillaChannelAddress remoteAddress = channel.getRemoteAddress();

                if (remoteAddress != null)
                {
                    int scopeIndex = channel.getLocalScope();

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doClose(channel, handlerFuture);
                }
            }
            catch (ChannelException ex)
            {
                fireExceptionCaught(channel, ex);
            }
        }
    }

    private final class SystemFlushTask implements Runnable
    {
        private final ZillaChannel channel;
        private final ChannelFuture flushFuture;

        private SystemFlushTask(
            ZillaChannel channel,
            ChannelFuture future)
        {
            this.channel = channel;
            this.flushFuture = future;
        }

        @Override
        public void run()
        {
            try
            {
                if (!channel.isWriteClosed())
                {
                    ZillaEngine engine = channel.engine;
                    int scopeIndex = channel.getLocalScope();

                    ZillaScope scope = engine.supplyScope(scopeIndex);
                    scope.doSystemFlush(channel, flushFuture);
                }
            }
            catch (Exception ex)
            {
                flushFuture.setFailure(ex);
            }
        }
    }
}
