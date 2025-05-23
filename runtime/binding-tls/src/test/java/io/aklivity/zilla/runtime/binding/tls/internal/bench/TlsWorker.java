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
package io.aklivity.zilla.runtime.binding.tls.internal.bench;

import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.isInitial;
import static java.lang.ThreadLocal.withInitial;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetAddress;
import java.nio.channels.SelectableChannel;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.zip.CRC32C;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingFactory;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.internal.layouts.BufferPoolLayout;
import io.aklivity.zilla.runtime.engine.internal.stream.StreamId;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.vault.Vault;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;
import io.aklivity.zilla.runtime.engine.vault.VaultFactory;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class TlsWorker implements EngineContext
{
    private static final int BUFFER_SIZE = 1024 * 64;
    private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[BUFFER_SIZE]);
    private final RingBuffer streamsBuffer = new OneToOneRingBuffer(new UnsafeBuffer(new byte[1024 * 1024 + 768]));
    private final BufferPool bufferPool;
    private final Long2ObjectHashMap<BindingHandler> handlers;
    private final Object2ObjectHashMap<String, Binding> bindings;
    private final Long2ObjectHashMap<VaultHandler> vaultHandlers;
    private final Object2ObjectHashMap<String, Vault> vaults;
    private final Long2ObjectHashMap<MessageConsumer> streamsById;
    private final Long2ObjectHashMap<MessageConsumer> throtllesById;
    private final BindingFactory factory;
    private final VaultFactory vaultFactory;
    private final Configuration config;
    private final Path configPath;

    private final TlsSignaler signaler;

    private final FrameFW frameRO = new FrameFW();
    private final CRC32C crc32C = new CRC32C();

    private int nextInitialId = 3;

    public TlsWorker(
        EngineConfiguration config)
    {
        this.config = config;
        this.bufferPool = new BufferPoolLayout.Builder()
                .path(config.directory().resolve(String.format("buffers%d", 0)))
                .slotCapacity(config.bufferSlotCapacity())
                .slotCount(config.bufferPoolCapacity() / config.bufferSlotCapacity())
                .readonly(false)
                .build()
                .bufferPool();
        this.configPath = Path.of(config.configURI());

        this.signaler = new TlsSignaler();

        this.factory = BindingFactory.instantiate();
        this.vaultFactory = VaultFactory.instantiate();
        this.bindings = new Object2ObjectHashMap<>();
        this.handlers = new Long2ObjectHashMap<>();
        this.vaultHandlers = new Long2ObjectHashMap<>();
        this.vaults = new Object2ObjectHashMap<>();
        this.streamsById = new Long2ObjectHashMap<>();
        this.throtllesById = new Long2ObjectHashMap<>();
    }

    @Override
    public int index()
    {
        return 0;
    }

    @Override
    public Signaler signaler()
    {
        return signaler;
    }

    @Override
    public int supplyTypeId(
        String name)
    {
        return 0;
    }

    @Override
    public long supplyInitialId(
        long bindingId)
    {
        return nextInitialId += 2;
    }

    @Override
    public long supplyReplyId(
        long initialId)
    {
        assert isInitial(initialId);
        return initialId & 0xffff_ffff_ffff_fffeL;
    }

    @Override
    public long supplyPromiseId(
        long initialId)
    {
        return 0;
    }

    @Override
    public long supplyAuthorizedId()
    {
        return 0;
    }

    @Override
    public long supplyBudgetId()
    {
        return 0;
    }

    @Override
    public long supplyTraceId()
    {
        return 0;
    }

    @Override
    public MessageConsumer supplySender(
        long streamId)
    {
        return null;
    }

    @Override
    public MessageConsumer supplyReceiver(
        long streamId)
    {
        return null;
    }

    @Override
    public EventFormatter supplyEventFormatter()
    {
        return null;
    }

    @Override
    public void report(
        Throwable ex)
    {
    }

    @Override
    public void attachComposite(
        NamespaceConfig composite)
    {
    }

    @Override
    public void detachComposite(
        NamespaceConfig composite)
    {
    }

    @Override
    public void detachSender(long replyId)
    {
    }

    @Override
    public void detachStreams(long bindingId)
    {
    }

    @Override
    public BudgetCreditor creditor()
    {
        return null;
    }

    @Override
    public BudgetDebitor supplyDebitor(long budgetId)
    {
        return null;
    }

    @Override
    public MutableDirectBuffer writeBuffer()
    {
        return writeBuffer;
    }

    @Override
    public BufferPool bufferPool()
    {
        return bufferPool;
    }

    @Override
    public LongSupplier supplyCounter(
        long bindingId,
        long metricId)
    {
        return null;
    }

    @Override
    public LongSupplier supplyGauge(
        long bindingId,
        long metricId)
    {
        return null;
    }

    @Override
    public LongSupplier[] supplyHistogram(
        long bindingId,
        long metricId)
    {
        return new LongSupplier[0];
    }

    @Override
    public MessageConsumer droppedFrameHandler()
    {
        return null;
    }

    @Override
    public int supplyClientIndex(
        long streamId)
    {
        return 0;
    }

    @Override
    public InetAddress[] resolveHost(
        String host)
    {
        return new InetAddress[0];
    }

    @Override
    public PollerKey supplyPollerKey(
        SelectableChannel channel)
    {
        return null;
    }

    @Override
    public long supplyBindingId(
        NamespaceConfig namespace,
        BindingConfig binding)
    {
        return 0;
    }

    @Override
    public String supplyNamespace(
        long namespacedId)
    {
        return "";
    }

    @Override
    public String supplyLocalName(
        long namespacedId)
    {
        return "";
    }

    @Override
    public String supplyQName(
        long namespacedId)
    {
        return "";
    }

    @Override
    public int supplyEventId(
        String name)
    {
        return 0;
    }

    @Override
    public String supplyEventName(
        int eventId)
    {
        return "";
    }

    @Override
    public BindingHandler streamFactory()
    {
        return this::newStream;
    }

    @Override
    public GuardHandler supplyGuard(
        long guardId)
    {
        return null;
    }

    @Override
    public VaultHandler supplyVault(
        long vaultId)
    {
        return vaultHandlers.get(vaultId);
    }

    @Override
    public CatalogHandler supplyCatalog(
        long catalogId)
    {
        return null;
    }

    @Override
    public ValidatorHandler supplyValidator(
        ModelConfig config)
    {
        return null;
    }

    @Override
    public ConverterHandler supplyReadConverter(
        ModelConfig config)
    {
        return null;
    }

    @Override
    public ConverterHandler supplyWriteConverter(
        ModelConfig config)
    {
        return null;
    }

    @Override
    public LongConsumer supplyUtilizationMetric()
    {
        return null;
    }

    @Override
    public Path resolvePath(
        String location)
    {
        return configPath.resolveSibling(location);
    }

    @Override
    public Metric resolveMetric(
        String name)
    {
        return null;
    }

    @Override
    public void onExporterAttached(
        long exporterId)
    {

    }

    @Override
    public void onExporterDetached(
        long exporterId)
    {

    }

    @Override
    public LongConsumer supplyMetricWriter(
        Metric.Kind kind,
        long bindingId,
        long metricId)
    {
        return null;
    }

    @Override
    public MessageConsumer supplyEventWriter()
    {
        return MessageConsumer.NOOP;
    }

    @Override
    public MessageReader supplyEventReader()
    {
        return null;
    }

    @Override
    public Clock clock()
    {
        return Clock.systemUTC();
    }

    public void doWork()
    {
        streamsBuffer.read(this::handleRead, Integer.MAX_VALUE);
    }

    public void attach(
        NamespaceConfig namespace)
    {
        namespace.vaults.forEach(v ->
        {
            Vault vault = supplyVault(v.type);
            VaultContext context = vault.supply(this);
            VaultHandler handler = context.attach(v);

            vaultHandlers.put(crc32c(v.name), handler);
        });

        namespace.bindings.stream()
            .peek(b -> b.id = crc32c(b.name))
            .map(b -> b.routes)
            .forEach(rs -> rs.stream()
                .peek(r -> r.id = crc32c(r.exit))
                .forEach(r -> r.authorized = (session, resolve) -> true));

        namespace.bindings.forEach(b ->
        {
            Binding binding = supplyBinding(b.type);
            BindingContext context = binding.supply(this);
            b.vaultId = b.vault != null ? crc32c(b.vault) : 0L;
            BindingHandler handler = context.attach(b);

            handlers.put(b.id, handler);
        });
    }

    private void handleRead(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        final FrameFW frame = frameRO.wrap(buffer, index, index + length);
        final long streamId = frame.streamId();

        final MessageConsumer stream = StreamId.isThrottle(msgTypeId)
            ? throtllesById.get(streamId)
            : streamsById.get(streamId);

        stream.accept(msgTypeId, buffer, index, length);
    }

    private MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        switch (msgTypeId)
        {
        case BeginFW.TYPE_ID:
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long routedId = frame.routedId();
            final long streamId = frame.streamId();

            if (StreamId.isInitial(streamId))
            {
                final BindingHandler handler = handlers.get(routedId);
                MessageConsumer stream = handler.newStream(msgTypeId, buffer, index, length, this::handleWrite);
                streamsById.put(streamId, stream);
                throtllesById.put(streamId, sender);

                long replyId = supplyReplyId(streamId);
                throtllesById.put(replyId, stream);
                streamsById.put(replyId, sender);
            }
            break;
        }

        return this::handleWrite;
    }

    private void handleWrite(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        streamsBuffer.write(msgTypeId, buffer, index, length);
    }

    private Binding supplyBinding(
        String type)
    {
        return bindings.computeIfAbsent(type, this::createBinding);
    }

    private Vault supplyVault(
        String type)
    {
        return vaults.computeIfAbsent(type, this::createVault);
    }

    private Vault createVault(
        String type)
    {
        return vaultFactory.create(type, config);
    }

    private Binding createBinding(
        String type)
    {
        return factory.create(type, config);
    }

    private long crc32c(
        String value)
    {
        crc32C.reset();
        crc32C.update(value.getBytes(UTF_8));

        return crc32C.getValue();
    }

    private static SignalFW.Builder newSignalRW(
        int capacity)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[capacity]);
        return new SignalFW.Builder().wrap(buffer, 0, buffer.capacity());
    }

    private final class TlsSignaler implements Signaler
    {
        private final ThreadLocal<SignalFW.Builder> signalRW;

        private TlsSignaler()
        {
            signalRW = withInitial(() -> newSignalRW(512));
        }

        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            handler.accept(signalId);

            return NO_CANCEL_ID;
        }

        @Override
        public long signalAt(
            long timeMillis,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            signal(originId, routedId, streamId, 0L, 0L,
                traceId, NO_CANCEL_ID, signalId, contextId);
            return NO_CANCEL_ID;
        }

        @Override
        public long signalTask(
            Runnable task,
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            try
            {
                task.run();
            }
            finally
            {
                final SignalFW signal = signalRW.get()
                    .rewrap()
                    .originId(originId)
                    .routedId(routedId)
                    .streamId(streamId)
                    .sequence(0L)
                    .acknowledge(0L)
                    .maximum(0)
                    .timestamp(0L)
                    .traceId(traceId)
                    .cancelId(NO_CANCEL_ID)
                    .signalId(signalId)
                    .contextId(contextId)
                    .build();

                streamsBuffer.write(signal.typeId(), signal.buffer(), signal.offset(), signal.sizeof());
            }

            return NO_CANCEL_ID;
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId)
        {
            signal(originId, routedId, streamId, 0L, 0L, traceId, NO_CANCEL_ID, signalId, contextId);
        }

        @Override
        public void signalNow(
            long originId,
            long routedId,
            long streamId,
            long traceId,
            int signalId,
            int contextId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            signal(originId, routedId, streamId, 0L, 0L, traceId, NO_CANCEL_ID, signalId, contextId,
                buffer, offset, length);
        }

        @Override
        public boolean cancel(
            long cancelId)
        {
            return true;
        }

        private void signal(
            long originId,
            long routedId,
            long streamId,
            long sequence,
            long acknowledge,
            long traceId,
            long cancelId,
            int signalId,
            int contextId)
        {
            //NOOP
        }

        private void signal(
            long originId,
            long routedId,
            long streamId,
            long sequence,
            long acknowledge,
            long traceId,
            long cancelId,
            int signalId,
            int contextId,
            DirectBuffer buffer,
            int offset,
            int length)
        {
            //NOOP
        }

        private void invokeAndSignal(
            Runnable task,
            long originId,
            long routedId,
            long streamId,
            long sequence,
            long acknowledge,
            long traceId,
            long cancelId,
            int signalId,
            int contextId)
        {
            try
            {
                task.run();
            }
            finally
            {
                signal(originId, routedId, streamId, sequence, acknowledge, traceId, cancelId, signalId, contextId);
            }
        }
    }
}
