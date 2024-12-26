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
package io.aklivity.zilla.runtime.binding.echo.internal.bench;

import java.net.InetAddress;
import java.nio.channels.SelectableChannel;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.LongSupplier;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
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
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class EchoWorker implements EngineContext
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[BUFFER_SIZE]);

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
    public void report(
        Throwable ex)
    {
    }

    @Override
    public void attachComposite(NamespaceConfig composite)
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
        return null;
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
        return null;
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
    public Path resolvePath(
        String location)
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
}
