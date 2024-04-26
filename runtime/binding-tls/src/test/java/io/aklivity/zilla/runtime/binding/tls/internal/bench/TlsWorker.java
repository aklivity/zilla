/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.InetAddress;
import java.net.URL;
import java.nio.channels.SelectableChannel;
import java.time.Clock;
import java.util.function.LongSupplier;
import java.util.zip.CRC32C;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

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
import io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class TlsWorker implements EngineContext
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[BUFFER_SIZE]);
    private final BufferPool bufferPool;
    private final Long2ObjectHashMap<BindingHandler> handlers;
    private final Object2ObjectHashMap<String, Binding> bindings;
    private final BindingFactory factory;
    private final Configuration config;

    private final FrameFW frameRO = new FrameFW();
    private final CRC32C crc32C = new CRC32C();

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

        this.factory = BindingFactory.instantiate();
        this.bindings = new Object2ObjectHashMap<>();
        this.handlers = new Long2ObjectHashMap<>();
    }

    @Override
    public int index()
    {
        return 0;
    }

    @Override
    public Signaler signaler()
    {
        return null;
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
        return 0;
    }

    @Override
    public long supplyReplyId(
        long initialId)
    {
        return 0;
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
        return null;
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
    public URL resolvePath(
        String path)
    {
        return null;
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
    public MessageConsumer supplyEventWriter()
    {
        return null;
    }

    @Override
    public MessageReader supplyEventReader()
    {
        return null;
    }

    @Override
    public Clock clock()
    {
        return null;
    }

    public void attach(
        NamespaceConfig namespace)
    {
        namespace.bindings.stream()
            .peek(b -> b.id = crc32c(b.name))
            .map(b -> b.routes)
            .forEach(rs -> rs.stream()
                .peek(r -> r.id = crc32c(r.exit))
                .forEach(r -> r.authorized = session -> true));

        namespace.bindings.forEach(b ->
        {
            Binding binding = supplyBinding(b.type);
            BindingContext context = binding.supply(this);
            BindingHandler handler = context.attach(b);

            handlers.put(b.id, handler);
        });
    }

    private MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final FrameFW frame = frameRO.wrap(buffer, index, length);
        final long bindingId = frame.routedId();

        final BindingHandler handler = handlers.get(bindingId);

        return handler.newStream(msgTypeId, buffer, index, length, sender);
    }

    private Binding supplyBinding(
        String type)
    {
        return bindings.computeIfAbsent(type, this::createBinding);
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
}
