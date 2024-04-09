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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_BUDGET_ID;
import static io.aklivity.zilla.runtime.engine.concurrent.Signaler.NO_CANCEL_ID;
import static io.aklivity.zilla.runtime.engine.internal.registry.MetricHandlerKind.ORIGIN;
import static io.aklivity.zilla.runtime.engine.internal.registry.MetricHandlerKind.ROUTED;
import static io.aklivity.zilla.runtime.engine.internal.stream.BudgetId.ownerIndex;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.clientIndex;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.instanceId;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.isInitial;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.serverIndex;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.streamId;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.streamIndex;
import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.throttleIndex;
import static io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW.FIELD_OFFSET_STREAM_ID;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;
import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.RECEIVED;
import static io.aklivity.zilla.runtime.engine.metrics.MetricContext.Direction.SENT;
import static java.lang.System.currentTimeMillis;
import static java.lang.ThreadLocal.withInitial;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.agrona.CloseHelper.quietClose;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.agrona.concurrent.AgentRunner.startOnThread;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SelectableChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import org.agrona.CloseHelper;
import org.agrona.DeadlineTimerWheel;
import org.agrona.DeadlineTimerWheel.TimerHandler;
import org.agrona.DirectBuffer;
import org.agrona.ErrorHandler;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.LongHashSet;
import org.agrona.collections.Object2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.AgentTerminationException;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.hints.ThreadHints;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.budget.BudgetCreditor;
import io.aklivity.zilla.runtime.engine.budget.BudgetDebitor;
import io.aklivity.zilla.runtime.engine.buffer.BufferPool;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.catalog.CatalogContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.concurrent.Signaler;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.engine.event.EventFormatterFactory;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.exporter.ExporterContext;
import io.aklivity.zilla.runtime.engine.exporter.ExporterHandler;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler;
import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.budget.DefaultBudgetCreditor;
import io.aklivity.zilla.runtime.engine.internal.budget.DefaultBudgetDebitor;
import io.aklivity.zilla.runtime.engine.internal.exporter.ExporterAgent;
import io.aklivity.zilla.runtime.engine.internal.layouts.BudgetsLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.BufferPoolLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.StreamsLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.ScalarsLayout;
import io.aklivity.zilla.runtime.engine.internal.poller.Poller;
import io.aklivity.zilla.runtime.engine.internal.stream.StreamId;
import io.aklivity.zilla.runtime.engine.internal.stream.Target;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ChallengeFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FrameFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.SignalFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.Metric;
import io.aklivity.zilla.runtime.engine.metrics.MetricContext;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.model.ConverterHandler;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.model.ModelContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.poller.PollerKey;
import io.aklivity.zilla.runtime.engine.util.function.LongLongFunction;
import io.aklivity.zilla.runtime.engine.vault.Vault;
import io.aklivity.zilla.runtime.engine.vault.VaultContext;
import io.aklivity.zilla.runtime.engine.vault.VaultHandler;

public class EngineWorker implements EngineContext, Agent
{
    private static final int RESERVED_SIZE = 33;

    private static final int SHIFT_SIZE = 56;

    private static final int SIGNAL_TASK_QUEUED = 1;

    private final FrameFW frameRO = new FrameFW();
    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final FlushFW flushRO = new FlushFW();
    private final WindowFW windowRO = new WindowFW();
    private final SignalFW signalRO = new SignalFW();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();

    private final int localIndex;
    private final EngineConfiguration config;
    private final URL configURL;
    private final LabelManager labels;
    private final String agentName;
    private final Function<String, InetAddress[]> resolveHost;
    private final boolean timestamps;
    private final Object2ObjectHashMap<Metric.Kind, LongLongFunction<LongConsumer>> metricWriterSuppliers;
    private final Map<String, MetricGroup> metricGroupsByName;
    private final StreamsLayout streamsLayout;
    private final BufferPoolLayout bufferPoolLayout;
    private final RingBuffer streamsBuffer;
    private final MutableDirectBuffer writeBuffer;
    private final Long2ObjectHashMap<LongHashSet> streamSets;
    private final Int2ObjectHashMap<MessageConsumer>[] streams;
    private final Int2ObjectHashMap<MessageConsumer>[] throttles;
    private final Int2ObjectHashMap<MessageConsumer> writersByIndex;
    private final Int2ObjectHashMap<Target> targetsByIndex;
    private final BufferPool bufferPool;
    private final long mask;
    private final MessageHandler readHandler;
    private final TimerHandler expireHandler;
    private final int readLimit;
    private final int expireLimit;
    private final IntFunction<MessageConsumer> supplyWriter;
    private final IntFunction<Target> newTarget;
    private final LongFunction<Affinity> resolveAffinity;

    private final Poller poller;

    private final DefaultBudgetCreditor creditor;
    private final Int2ObjectHashMap<DefaultBudgetDebitor> debitorsByIndex;

    private final Long2ObjectHashMap<Affinity> affinityByBindingId;

    private final DeadlineTimerWheel timerWheel;
    private final Long2ObjectHashMap<Runnable> tasksByTimerId;
    private final Long2ObjectHashMap<Future<?>> futuresById;
    private final ElektronSignaler signaler;
    private final Long2ObjectHashMap<MessageConsumer> correlations;
    private final Long2ObjectHashMap<AgentRunner> exportersById;
    private final Map<String, ModelContext> modelsByType;

    private final EngineRegistry registry;
    private final Deque<Runnable> taskQueue;
    private final LongUnaryOperator affinityMask;
    private final AgentRunner runner;
    private final IdleStrategy idleStrategy;
    private final ErrorHandler errorHandler;
    private final ScalarsLayout countersLayout;
    private final ScalarsLayout gaugesLayout;
    private final HistogramsLayout histogramsLayout;
    private final EventsLayout eventsLayout;
    private final Supplier<MessageReader> supplyEventReader;
    private final EventFormatter eventFormatter;

    private long initialId;
    private long promiseId;
    private long traceId;
    private long budgetId;
    private long authorizedId;

    private long lastReadStreamId;

    private volatile Thread thread;

    public EngineWorker(
        EngineConfiguration config,
        ExecutorService executor,
        LabelManager labels,
        ErrorHandler errorHandler,
        LongUnaryOperator affinityMask,
        Collection<Binding> bindings,
        Collection<Exporter> exporters,
        Collection<Guard> guards,
        Collection<Vault> vaults,
        Collection<Catalog> catalogs,
        Collection<Model> models,
        Collection<MetricGroup> metricGroups,
        Collector collector,
        Supplier<MessageReader> supplyEventReader,
        EventFormatterFactory eventFormatterFactory,
        int index,
        boolean readonly)
    {
        this.localIndex = index;
        this.config = config;
        this.configURL = config.configURL();
        this.labels = labels;
        this.affinityMask = affinityMask;

        final IdleStrategy idleStrategy = new BackoffIdleStrategy(
                config.maxSpins(),
                config.maxYields(),
                config.minParkNanos(),
                config.maxParkNanos());

        this.countersLayout = new ScalarsLayout.Builder()
                .path(config.directory().resolve(String.format("metrics/counters%d", index)))
                .capacity(config.countersBufferCapacity())
                .readonly(readonly)
                .label("counters")
                .build();

        this.gaugesLayout = new ScalarsLayout.Builder()
                .path(config.directory().resolve(String.format("metrics/gauges%d", index)))
                .capacity(config.countersBufferCapacity())
                .readonly(readonly)
                .label("gauges")
                .build();

        this.histogramsLayout = new HistogramsLayout.Builder()
                .path(config.directory().resolve(String.format("metrics/histograms%d", index)))
                .capacity(config.countersBufferCapacity())
                .readonly(readonly)
                .build();

        metricWriterSuppliers = new Object2ObjectHashMap<>();
        metricWriterSuppliers.put(COUNTER, countersLayout::supplyWriter);
        metricWriterSuppliers.put(GAUGE, gaugesLayout::supplyWriter);
        metricWriterSuppliers.put(HISTOGRAM, histogramsLayout::supplyWriter);

        final StreamsLayout streamsLayout = new StreamsLayout.Builder()
                .path(config.directory().resolve(String.format("data%d", index)))
                .streamsCapacity(config.streamsBufferCapacity())
                .readonly(readonly)
                .build();

        final BufferPoolLayout bufferPoolLayout = new BufferPoolLayout.Builder()
                .path(config.directory().resolve(String.format("buffers%d", index)))
                .slotCapacity(config.bufferSlotCapacity())
                .slotCount(config.bufferPoolCapacity() / config.bufferSlotCapacity())
                .readonly(readonly)
                .build();

        this.eventsLayout = new EventsLayout.Builder()
            .path(config.directory().resolve(String.format("events%d", index)))
            .capacity(config.eventsBufferCapacity())
            .build();

        this.agentName = String.format("engine/data#%d", index);
        this.streamsLayout = streamsLayout;
        this.bufferPoolLayout = bufferPoolLayout;
        this.runner = new AgentRunner(idleStrategy, errorHandler, null, this);

        this.resolveHost = config.hostResolver();
        this.timestamps = config.timestamps();
        this.readLimit = config.maximumMessagesPerRead();
        this.expireLimit = config.maximumExpirationsPerPoll();
        this.streamsBuffer = streamsLayout.streamsBuffer();
        this.writeBuffer = new UnsafeBuffer(new byte[config.bufferSlotCapacity() + 1024]);
        this.streamSets = new Long2ObjectHashMap<>();
        this.streams = initDispatcher();
        this.throttles = initDispatcher();
        this.readHandler = this::handleRead;
        this.expireHandler = this::handleExpire;
        this.supplyWriter = this::supplyWriter;
        this.newTarget = this::newTarget;
        this.resolveAffinity = this::resolveAffinity;
        this.affinityByBindingId = new Long2ObjectHashMap<>();
        this.targetsByIndex = new Int2ObjectHashMap<>();
        this.writersByIndex = new Int2ObjectHashMap<>();

        this.timerWheel = new DeadlineTimerWheel(MILLISECONDS, currentTimeMillis(), 512, 1024);
        this.tasksByTimerId = new Long2ObjectHashMap<>();
        this.futuresById = new Long2ObjectHashMap<>();
        this.signaler = new ElektronSignaler(executor, Math.max(config.bufferSlotCapacity(), 512));

        this.poller = new Poller();

        final BufferPool bufferPool = bufferPoolLayout.bufferPool();

        final long initial = ((long) index) << SHIFT_SIZE;
        final long mask = initial | (-1L >>> RESERVED_SIZE);

        this.mask = mask;
        this.bufferPool = bufferPool;
        this.initialId = initial;
        this.promiseId = initial;
        this.traceId = initial;
        this.budgetId = initial;
        this.authorizedId = initial;

        final BudgetsLayout budgetsLayout = new BudgetsLayout.Builder()
                .path(config.directory().resolve(String.format("budgets%d", index)))
                .capacity(config.budgetsBufferCapacity())
                .owner(true)
                .build();

        this.creditor = new DefaultBudgetCreditor(index, budgetsLayout, this::doSystemFlush, this::supplyBudgetId,
            signaler::executeTaskAt, config.childCleanupLingerMillis());
        this.debitorsByIndex = new Int2ObjectHashMap<DefaultBudgetDebitor>();

        Map<String, BindingContext> bindingsByType = new LinkedHashMap<>();
        for (Binding binding : bindings)
        {
            String type = binding.name();
            bindingsByType.put(type, binding.supply(this));
        }

        Map<String, ExporterContext> exportersByType = new LinkedHashMap<>();
        for (Exporter exporter : exporters)
        {
            String type = exporter.name();
            exportersByType.put(type, exporter.supply(this));
        }

        Map<String, GuardContext> guardsByType = new LinkedHashMap<>();
        for (Guard guard : guards)
        {
            String type = guard.name();
            guardsByType.put(type, guard.supply(this));
        }

        Map<String, VaultContext> vaultsByType = new LinkedHashMap<>();
        for (Vault vault : vaults)
        {
            String type = vault.name();
            vaultsByType.put(type, vault.supply(this));
        }

        Map<String, CatalogContext> catalogsByType = new LinkedHashMap<>();
        for (Catalog catalog : catalogs)
        {
            String type = catalog.name();
            catalogsByType.put(type, catalog.supply(this));
        }

        Map<String, ModelContext> modelsByType = new LinkedHashMap<>();
        for (Model model : models)
        {
            String type = model.name();
            modelsByType.put(type, model.supply(this));
        }
        this.modelsByType = modelsByType;

        Map<String, MetricContext> metricsByName = new LinkedHashMap<>();
        for (MetricGroup metricGroup : metricGroups)
        {
            for (String metricName : metricGroup.metricNames())
            {
                Metric metric = metricGroup.supply(metricName);
                metricsByName.put(metricName, metric.supply(this));
            }
        }

        this.metricGroupsByName = new Object2ObjectHashMap<>();
        for (MetricGroup metricGroup : metricGroups)
        {
            metricGroupsByName.put(metricGroup.name(), metricGroup);
        }

        this.registry = new EngineRegistry(
                bindingsByType::get, guardsByType::get, vaultsByType::get, catalogsByType::get, metricsByName::get,
                exportersByType::get, labels::supplyLabelId, this::onExporterAttached, this::onExporterDetached,
                this::supplyMetricWriter, this::detachStreams, collector);

        this.taskQueue = new ConcurrentLinkedDeque<>();
        this.correlations = new Long2ObjectHashMap<>();
        this.idleStrategy = idleStrategy;
        this.errorHandler = errorHandler;
        this.exportersById = new Long2ObjectHashMap<>();
        this.supplyEventReader = supplyEventReader;
        this.eventFormatter = eventFormatterFactory.create(config, this);
    }

    public static int indexOfId(
        long indexedId)
    {
        return (int) (indexedId >> SHIFT_SIZE);
    }

    @Override
    public int index()
    {
        return localIndex;
    }

    @Override
    public Signaler signaler()
    {
        return signaler;
    }

    @Override
    public String supplyNamespace(
        long namespacedId)
    {
        return labels.lookupLabel(NamespacedId.namespaceId(namespacedId));
    }

    @Override
    public String supplyLocalName(
        long namespacedId)
    {
        return labels.lookupLabel(NamespacedId.localId(namespacedId));
    }

    @Override
    public String supplyQName(
        long namespacedId)
    {
        return String.format("%s.%s", labels.lookupLabel(NamespacedId.namespaceId(namespacedId)),
            labels.lookupLabel(NamespacedId.localId(namespacedId)));
    }

    @Override
    public int supplyEventId(
        String name)
    {
        return labels.supplyLabelId(name);
    }

    @Override
    public int supplyTypeId(
        String name)
    {
        return labels.supplyLabelId(name);
    }

    @Override
    public long supplyInitialId(
        long bindingId)
    {
        final int remoteIndex = resolveRemoteIndex(bindingId);

        initialId += 2L;
        initialId &= mask;

        return (((long)remoteIndex << 48) & 0x00ff_0000_0000_0000L) |
               (initialId & 0xff00_0000_7fff_ffffL) | 0x0000_0000_0000_0001L;
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
        long carrierId)
    {
        promiseId += 2L;
        promiseId &= mask;

        return carrierId & 0xffff_0000_0000_0000L | 0x0000_0000_8000_0000L |
                promiseId & 0x0000_0000_7fff_ffffL | 0x0000_0000_0000_0001L;
    }

    @Override
    public long supplyAuthorizedId()
    {
        authorizedId++;
        authorizedId &= mask;
        return authorizedId;
    }

    @Override
    public long supplyBudgetId()
    {
        budgetId++;
        budgetId &= mask;
        return budgetId;
    }

    @Override
    public long supplyTraceId()
    {
        traceId++;
        traceId &= mask;
        return traceId;
    }

    @Override
    public void detachSender(
        long streamId)
    {
        throttles[throttleIndex(streamId)].remove(instanceId(streamId));
    }

    @Override
    public void detachStreams(
        long bindingId)
    {
        LongHashSet streamIdSet = streamSets.remove(bindingId);
        if (streamIdSet != null)
        {
            streamIdSet.forEach(streamId ->
                {
                    MessageConsumer handler = streams[streamIndex(streamId)].remove(instanceId(streamId));
                    if (handler != null)
                    {
                        doSyntheticAbort(streamId, handler);
                        doSyntheticReset(streamId, supplyWriter(streamIndex(streamId)));
                    }
                }
            );
        }
    }

    @Override
    public BudgetCreditor creditor()
    {
        return creditor;
    }

    @Override
    public BudgetDebitor supplyDebitor(
        long budgetId)
    {
        final int ownerIndex = ownerIndex(budgetId);
        return debitorsByIndex.computeIfAbsent(ownerIndex, this::newBudgetDebitor);
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
        return countersLayout.supplyReader(bindingId, metricId);
    }

    @Override
    public LongSupplier supplyGauge(
        long bindingId,
        long metricId)
    {
        return gaugesLayout.supplyReader(bindingId, metricId);
    }

    @Override
    public LongSupplier[] supplyHistogram(
        long bindingId,
        long metricId)
    {
        return histogramsLayout.supplyReaders(bindingId, metricId);
    }

    public long[][] counterIds()
    {
        return countersLayout.getIds();
    }

    public long[][] gaugeIds()
    {
        return gaugesLayout.getIds();
    }

    public long[][] histogramIds()
    {
        return histogramsLayout.getIds();
    }

    @Override
    public MessageConsumer droppedFrameHandler()
    {
        return this::handleDroppedReadFrame;
    }

    @Override
    public int supplyClientIndex(
        long streamId)
    {
        return clientIndex(streamId);
    }

    @Override
    public InetAddress[] resolveHost(
        String host)
    {
        return resolveHost.apply(host);
    }

    @Override
    public PollerKey supplyPollerKey(
        SelectableChannel channel)
    {
        return poller.register(channel);
    }

    @Override
    public long supplyBindingId(
        NamespaceConfig namespace,
        BindingConfig binding)
    {
        final int namespaceId = labels.supplyLabelId(namespace.name);
        final int bindingId = labels.supplyLabelId(binding.name);
        return NamespacedId.id(namespaceId, bindingId);
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
        GuardRegistry guard = registry.resolveGuard(guardId);
        return guard != null ? guard.handler() : null;
    }

    @Override
    public VaultHandler supplyVault(
        long vaultId)
    {
        VaultRegistry vault = registry.resolveVault(vaultId);
        return vault != null ? vault.handler() : null;
    }

    @Override
    public CatalogHandler supplyCatalog(
        long catalogId)
    {
        CatalogRegistry catalog = registry.resolveCatalog(catalogId);
        return catalog != null ? catalog.handler() : null;
    }

    @Override
    public ValidatorHandler supplyValidator(
        ModelConfig config)
    {
        ModelContext model = modelsByType.get(config.model);
        return model != null ? model.supplyValidatorHandler(config) : null;
    }

    @Override
    public ConverterHandler supplyReadConverter(
        ModelConfig config)
    {
        ModelContext model = modelsByType.get(config.model);
        return model != null ? model.supplyReadConverterHandler(config) : null;
    }

    @Override
    public ConverterHandler supplyWriteConverter(
        ModelConfig config)
    {
        ModelContext model = modelsByType.get(config.model);
        return model != null ? model.supplyWriteConverterHandler(config) : null;
    }

    @Override
    public URL resolvePath(
        String path)
    {
        URL resolved = null;
        try
        {
            resolved = new URL(configURL, path);
        }
        catch (MalformedURLException ex)
        {
            rethrowUnchecked(ex);
        }
        return resolved;
    }

    @Override
    public String roleName()
    {
        return agentName;
    }

    public void doStart()
    {
        thread = startOnThread(runner, Thread::new);
    }

    public void doClose()
    {
        CloseHelper.close(runner);
        thread = null;
    }

    @Override
    public int doWork()
    {
        int workDone = 0;

        try
        {
            workDone += poller.doWork();

            if (timerWheel.timerCount() != 0L)
            {
                final long now = currentTimeMillis();
                int expiredMax = expireLimit;
                while (timerWheel.currentTickTime() <= now && expiredMax > 0)
                {
                    final int expired = timerWheel.poll(now, expireHandler, expiredMax);

                    workDone += expired;
                    expiredMax -= expired;
                }
            }

            workDone += streamsBuffer.read(readHandler, readLimit);
        }
        catch (Throwable ex)
        {
            ex.addSuppressed(new Exception(String.format("[%s]\t[0x%016x] %s",
                                                         agentName, lastReadStreamId, streamsLayout)));
            throw new AgentTerminationException(ex);
        }

        return workDone;
    }

    @Override
    public void onClose()
    {
        registry.detachAll();

        poller.onClose();

        int acquiredBuffers = 0;
        int acquiredCreditors = 0;
        long acquiredDebitors = 0L;

        if (config.syntheticAbort())
        {
            final Int2ObjectHashMap<MessageConsumer> handlers = new Int2ObjectHashMap<>();
            for (int senderIndex = 0; senderIndex < streams.length; senderIndex++)
            {
                handlers.clear();
                streams[senderIndex].forEach(handlers::put);

                final int senderIndex0 = senderIndex;
                handlers.forEach((id, handler) -> doSyntheticAbort(streamId(localIndex, senderIndex0, id), handler));
            }

            acquiredBuffers = bufferPool.acquiredSlots();
            acquiredCreditors = creditor.acquired();
            acquiredDebitors = debitorsByIndex.values()
                                              .stream()
                                              .mapToInt(DefaultBudgetDebitor::acquired)
                                              .sum();
        }

        targetsByIndex.forEach((k, v) -> v.detach());
        targetsByIndex.forEach((k, v) -> quietClose(v));

        quietClose(streamsLayout);
        quietClose(bufferPoolLayout);

        debitorsByIndex.forEach((k, v) -> quietClose(v));
        quietClose(creditor);

        if (acquiredBuffers != 0 || acquiredCreditors != 0 || acquiredDebitors != 0L)
        {
            throw new IllegalStateException(
                    String.format("Some resources not released: %d buffers, %d creditors, %d debitors",
                                  acquiredBuffers, acquiredCreditors, acquiredDebitors));
        }
    }

    public void drain()
    {
        final long closeAt = System.nanoTime();
        while (streamsBuffer.consumerPosition() < streamsBuffer.producerPosition())
        {
            ThreadHints.onSpinWait();

            if (System.nanoTime() - closeAt >= Duration.ofSeconds(30).toNanos())
            {
                break;
            }
        }
    }

    @Override
    public String toString()
    {
        return agentName;
    }

    public CompletableFuture<Void> attach(
        NamespaceConfig namespace)
    {
        NamespaceTask attachTask = registry.attach(namespace);

        if (thread == Thread.currentThread())
        {
            attachTask.run();
        }
        else
        {
            taskQueue.offer(attachTask);
            signaler.signalNow(0L, 0L, 0L, supplyTraceId(), SIGNAL_TASK_QUEUED, 0);
        }

        return attachTask.future();
    }

    public CompletableFuture<Void> detach(
        NamespaceConfig namespace)
    {
        NamespaceTask detachTask = registry.detach(namespace);

        if (thread == Thread.currentThread())
        {
            detachTask.run();
        }
        else
        {
            taskQueue.offer(detachTask);
            signaler.signalNow(0L, 0L, 0L, supplyTraceId(), SIGNAL_TASK_QUEUED, 0);
        }

        return detachTask.future();
    }

    public AgentRunner runner()
    {
        return runner;
    }

    @Override
    public void onExporterAttached(
        long exporterId)
    {
        if (localIndex == 0)
        {
            ExporterRegistry exporter = registry.resolveExporter(exporterId);
            ExporterHandler handler = exporter.handler();
            ExporterAgent agent = new ExporterAgent(exporterId, handler);
            AgentRunner runner = new AgentRunner(idleStrategy, errorHandler, null, agent);
            AgentRunner.startOnThread(runner);
            exportersById.put(exporterId, runner);
        }
    }

    @Override
    public void onExporterDetached(
        long exporterId)
    {
        if (localIndex == 0)
        {
            AgentRunner runner = exportersById.remove(exporterId);
            if (runner != null)
            {
                runner.close();
            }
        }
    }

    @Override
    public Metric resolveMetric(
        String metricName)
    {
        String metricGroupName = metricName.split("\\.")[0];
        return metricGroupsByName.get(metricGroupName).supply(metricName);
    }

    // required for testing
    public LongConsumer supplyCounterWriter(
        long bindingId,
        long metricId)
    {
        return countersLayout.supplyWriter(bindingId, metricId);
    }

    // required for testing
    public LongConsumer supplyGaugeWriter(
        long bindingId,
        long metricId)
    {
        return gaugesLayout.supplyWriter(bindingId, metricId);
    }

    // required for testing
    public LongConsumer supplyHistogramWriter(
        long bindingId,
        long metricId)
    {
        return histogramsLayout.supplyWriter(bindingId, metricId);
    }

    @Override
    public MessageConsumer supplyEventWriter()
    {
        return this.eventsLayout::writeEvent;
    }

    @Override
    public Clock clock()
    {
        return Clock.systemUTC();
    }

    private void onSystemMessage(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case FlushFW.TYPE_ID:
            final FlushFW flush = flushRO.wrap(buffer, index, index + length);
            onSystemFlush(flush);
            break;
        case WindowFW.TYPE_ID:
            final WindowFW window = windowRO.wrap(buffer, index, index + length);
            onSystemWindow(window);
            break;
        case SignalFW.TYPE_ID:
            final SignalFW signal = signalRO.wrap(buffer, index, index + length);
            onSystemSignal(signal);
            break;
        }
    }

    private void onSystemFlush(
        FlushFW flush)
    {
        final long traceId = flush.traceId();
        final long budgetId = flush.budgetId();

        final int ownerIndex = ownerIndex(budgetId);
        final DefaultBudgetDebitor debitor = debitorsByIndex.get(ownerIndex);

        if (EngineConfiguration.DEBUG_BUDGETS)
        {
            System.out.format("[%d] [0x%016x] [0x%016x] FLUSH %08x %s\n",
                    System.nanoTime(), traceId, budgetId, ownerIndex, debitor);
        }

        if (debitor != null)
        {
            debitor.flush(traceId, budgetId);
        }
    }

    private void onSystemWindow(
        WindowFW window)
    {
        final long traceId = window.traceId();
        final long budgetId = window.budgetId();
        final int reserved = window.maximum();

        creditor.creditById(traceId, budgetId, reserved);

        long parentBudgetId = creditor.parentBudgetId(budgetId);
        if (parentBudgetId != NO_BUDGET_ID)
        {
            doSystemWindowIfNecessary(traceId, parentBudgetId, reserved);
        }
    }

    private void onSystemSignal(
        SignalFW signal)
    {
        final int signalId = signal.signalId();

        switch (signalId)
        {
        case SIGNAL_TASK_QUEUED:
            taskQueue.poll().run();
            break;
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
                if (EngineConfiguration.DEBUG_BUDGETS)
                {
                    System.out.format("[%d] [0x%016x] [0x%016x] flush %d\n",
                            System.nanoTime(), traceId, budgetId, watcherIndex);
                }

                final MessageConsumer writer = supplyWriter(watcherIndex);
                final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                        .originId(0L)
                        .routedId(0L)
                        .streamId(0L)
                        .sequence(0L)
                        .acknowledge(0L)
                        .maximum(0)
                        .traceId(traceId)
                        .budgetId(budgetId)
                        .reserved(0)
                        .build();

                writer.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
            }
        }
    }

    private void doSystemWindow(
        long traceId,
        long budgetId,
        int reserved)
    {
        if (EngineConfiguration.DEBUG_BUDGETS)
        {
            System.out.format("[%d] [0x%016x] [0x%016x] doSystemWindow credit=%d \n",
                System.nanoTime(), traceId, budgetId, reserved);
        }

        final int targetIndex = ownerIndex(budgetId);
        final MessageConsumer writer = supplyWriter(targetIndex);
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(0L)
            .routedId(0L)
            .streamId(0L)
            .sequence(0L)
            .acknowledge(0L)
            .maximum(reserved)
            .traceId(traceId)
            .budgetId(budgetId)
            .padding(0)
            .build();
        writer.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private boolean handleExpire(
        TimeUnit timeUnit,
        long now,
        long timerId)
    {
        final Runnable task = tasksByTimerId.remove(timerId);
        if (task != null)
        {
            task.run();
        }
        return true;
    }

    private void handleRead(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        final FrameFW frame = frameRO.wrap(buffer, index, index + length);
        final long originId = frame.originId();
        final long routedId = frame.routedId();
        final long streamId = frame.streamId();
        final long sequence = frame.sequence();
        final long acknowledge = frame.acknowledge();
        final int maximum = frame.maximum();

        this.lastReadStreamId = streamId;

        if (streamId == 0L)
        {
            onSystemMessage(msgTypeId, buffer, index, length);
        }
        else if (isInitial(streamId))
        {
            handleReadInitial(originId, routedId, streamId, sequence, acknowledge, maximum, msgTypeId, buffer, index, length);
        }
        else
        {
            handleReadReply(originId, routedId, streamId, sequence, acknowledge, maximum, msgTypeId, buffer, index, length);
        }
    }

    private void handleReadInitial(
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index, int length)
    {
        final int instanceId = instanceId(streamId);

        if ((msgTypeId & 0x4000_0000) == 0)
        {
            final Int2ObjectHashMap<MessageConsumer> dispatcher = streams[streamIndex(streamId)];
            final MessageConsumer handler = dispatcher.get(instanceId);
            if (handler != null)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    break;
                case DataFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    break;
                case EndFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    dispatcher.remove(instanceId);
                    break;
                case AbortFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    dispatcher.remove(instanceId);
                    break;
                case FlushFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    break;
                default:
                    doReset(originId, routedId, streamId, sequence, acknowledge, maximum);
                    break;
                }
            }
            else
            {
                handleDefaultReadInitial(msgTypeId, buffer, index, length);
            }
        }
        else
        {
            final Int2ObjectHashMap<MessageConsumer> dispatcher = throttles[throttleIndex(streamId)];
            final MessageConsumer throttle = dispatcher.get(instanceId);
            if (throttle != null)
            {
                switch (msgTypeId)
                {
                case WindowFW.TYPE_ID:
                    throttle.accept(msgTypeId, buffer, index, length);
                    break;
                case ResetFW.TYPE_ID:
                    throttle.accept(msgTypeId, buffer, index, length);
                    dispatcher.remove(instanceId);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    final long cancelId = signal.cancelId();
                    if (cancelId != NO_CANCEL_ID)
                    {
                        futuresById.remove(cancelId);
                    }
                    throttle.accept(msgTypeId, buffer, index, length);
                    break;
                case ChallengeFW.TYPE_ID:
                    throttle.accept(msgTypeId, buffer, index, length);
                    break;
                default:
                    break;
                }
            }
            else
            {
                switch (msgTypeId)
                {
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    final long cancelId = signal.cancelId();
                    if (cancelId != NO_CANCEL_ID)
                    {
                        futuresById.remove(cancelId);
                    }
                    break;
                }
            }
        }
    }

    private void handleDefaultReadInitial(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case BeginFW.TYPE_ID:
            final MessageConsumer newHandler = handleBeginInitial(msgTypeId, buffer, index, length);
            if (newHandler != null)
            {
                newHandler.accept(msgTypeId, buffer, index, length);
            }
            else
            {
                final FrameFW frame = frameRO.wrap(buffer, index, index + length);
                final long originId = frame.originId();
                final long routedId = frame.routedId();
                final long streamId = frame.streamId();
                final long sequence = frame.sequence();
                final long acknowledge = frame.acknowledge();
                final int maximum = frame.maximum();

                doReset(originId, routedId, streamId, sequence, acknowledge, maximum);
            }
            break;
        case DataFW.TYPE_ID:
            handleDroppedReadData(msgTypeId, buffer, index, length);
            break;
        }
    }

    private void handleDroppedReadFrame(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case DataFW.TYPE_ID:
            handleDroppedReadData(msgTypeId, buffer, index, length);
            break;
        }
    }

    private void handleDroppedReadData(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        assert msgTypeId == DataFW.TYPE_ID;

        final DataFW data = dataRO.wrap(buffer, index, index + length);
        final long traceId = data.traceId();
        final long budgetId = data.budgetId();
        final int reserved = data.reserved();

        doSystemWindowIfNecessary(traceId, budgetId, reserved);
    }

    private void doSystemWindowIfNecessary(
        long traceId,
        long budgetId,
        int reserved)
    {
        if (budgetId != 0L && reserved > 0)
        {
            doSystemWindow(traceId, budgetId, reserved);
        }
    }

    private void handleReadReply(
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index, int length)
    {
        final int instanceId = instanceId(streamId);

        if ((msgTypeId & 0x4000_0000) == 0)
        {
            final Int2ObjectHashMap<MessageConsumer> dispatcher = streams[streamIndex(streamId)];
            final MessageConsumer handler = dispatcher.get(instanceId);
            if (handler != null)
            {
                switch (msgTypeId)
                {
                case BeginFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    break;
                case DataFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    break;
                case EndFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    dispatcher.remove(instanceId);
                    break;
                case AbortFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    dispatcher.remove(instanceId);
                    break;
                case FlushFW.TYPE_ID:
                    handler.accept(msgTypeId, buffer, index, length);
                    break;
                default:
                    doReset(originId, routedId, streamId, sequence, acknowledge, maximum);
                    break;
                }
            }
            else
            {
                handleDefaultReadReply(msgTypeId, buffer, index, length);
            }
        }
        else
        {
            final Int2ObjectHashMap<MessageConsumer> dispatcher = throttles[throttleIndex(streamId)];
            final MessageConsumer throttle = dispatcher.get(instanceId);
            if (throttle != null)
            {
                switch (msgTypeId)
                {
                case WindowFW.TYPE_ID:
                    throttle.accept(msgTypeId, buffer, index, length);
                    break;
                case ResetFW.TYPE_ID:
                    throttle.accept(msgTypeId, buffer, index, length);
                    dispatcher.remove(instanceId);
                    break;
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    final long cancelId = signal.cancelId();
                    if (cancelId != NO_CANCEL_ID)
                    {
                        futuresById.remove(cancelId);
                    }
                    throttle.accept(msgTypeId, buffer, index, length);
                    break;
                case ChallengeFW.TYPE_ID:
                    throttle.accept(msgTypeId, buffer, index, length);
                    break;
                default:
                    break;
                }
            }
            else
            {
                switch (msgTypeId)
                {
                case SignalFW.TYPE_ID:
                    final SignalFW signal = signalRO.wrap(buffer, index, index + length);
                    final long cancelId = signal.cancelId();
                    if (cancelId != NO_CANCEL_ID)
                    {
                        futuresById.remove(cancelId);
                    }
                    break;
                }
            }
        }
    }

    private void handleDefaultReadReply(
        int msgTypeId,
        MutableDirectBuffer buffer,
        int index,
        int length)
    {
        if (msgTypeId == BeginFW.TYPE_ID)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long originId = frame.originId();
            final long routedId = frame.routedId();
            final long streamId = frame.streamId();
            final long sequence = frame.sequence();
            final long acknowledge = frame.acknowledge();
            final int maximum = frame.maximum();
            final MessageConsumer newHandler = handleBeginReply(msgTypeId, buffer, index, length);
            if (newHandler != null)
            {
                newHandler.accept(msgTypeId, buffer, index, length);
            }
            else
            {
                doReset(originId, routedId, streamId, sequence, acknowledge, maximum);
            }
        }
        else if (msgTypeId == DataFW.TYPE_ID)
        {
            handleDroppedReadData(msgTypeId, buffer, index, length);
        }
        else if (msgTypeId == FlushFW.TYPE_ID)
        {
            final FrameFW frame = frameRO.wrap(buffer, index, index + length);
            final long originId = frame.originId();
            final long routedId = frame.routedId();
            final long streamId = frame.streamId();
            final long sequence = frame.sequence();
            final long acknowledge = frame.acknowledge();
            final int maximum = frame.maximum();

            final MessageConsumer newHandler = handleFlushReply(msgTypeId, buffer, index, length);
            if (newHandler != null)
            {
                newHandler.accept(msgTypeId, buffer, index, length);
            }
            else
            {
                doReset(originId, routedId, streamId, sequence, acknowledge, maximum);
            }
        }
    }

    private MessageConsumer handleBeginInitial(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();

        MessageConsumer newStream = null;

        BindingRegistry binding = registry.resolveBinding(routedId);
        final BindingHandler streamFactory = binding != null ? binding.streamFactory() : null;
        if (streamFactory != null)
        {
            MessageConsumer sentMetricHandler = supplyMetricRecorder(originId, ORIGIN, SENT)
                .andThen(supplyMetricRecorder(routedId, ROUTED, SENT));
            MessageConsumer receivedMetricHandler = supplyMetricRecorder(originId, ORIGIN, RECEIVED)
                .andThen(supplyMetricRecorder(routedId, ROUTED, RECEIVED));
            final MessageConsumer replyTo = supplyReplyTo(initialId)
                .andThen(sentMetricHandler.filter(this::isReplyId))
                .andThen(receivedMetricHandler.filter(this::isInitialId));
            newStream = streamFactory.newStream(msgTypeId, buffer, index, length, replyTo);
            if (newStream != null)
            {
                newStream = receivedMetricHandler.filter(this::isInitialId)
                    .andThen(sentMetricHandler.filter(this::isReplyId))
                    .andThen(newStream);

                final long replyId = supplyReplyId(initialId);
                streams[streamIndex(initialId)].put(instanceId(initialId), newStream);
                throttles[throttleIndex(replyId)].put(instanceId(replyId), newStream);
                streamSets.computeIfAbsent(routedId, k -> new LongHashSet())
                    .add(initialId);
            }
        }

        return newStream;
    }

    private boolean isInitialId(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        return StreamId.isInitial(buffer.getLong(index + FIELD_OFFSET_STREAM_ID));
    }

    private boolean isReplyId(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        return !StreamId.isInitial(buffer.getLong(index + FIELD_OFFSET_STREAM_ID));
    }

    private MessageConsumer handleBeginReply(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long streamId = begin.streamId();

        MessageConsumer newStream = null;

        newStream = correlations.remove(streamId);
        if (newStream != null)
        {
            streams[streamIndex(streamId)].put(instanceId(streamId), newStream);
        }

        return newStream;
    }

    private MessageConsumer handleFlushReply(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        final FlushFW flush = flushRO.wrap(buffer, index, index + length);
        final long streamId = flush.streamId();

        MessageConsumer newStream = null;

        newStream = correlations.get(streamId);

        return newStream;
    }

    private void doReset(
        final long originId,
        final long routedId,
        final long streamId,
        final long sequence,
        final long acknowledge, final int maximum)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .build();

        final MessageConsumer replyTo = supplyReplyTo(streamId);
        replyTo.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doSyntheticReset(
        long streamId,
        MessageConsumer throttle)
    {
        final long syntheticId = 0L;

        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(syntheticId)
            .routedId(syntheticId)
            .streamId(streamId)
            .sequence(Long.MAX_VALUE)
            .acknowledge(0L)
            .maximum(0)
            .build();

        throttle.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private void doSyntheticAbort(
        long streamId,
        MessageConsumer stream)
    {
        final long syntheticId = 0L;

        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
            .originId(syntheticId)
            .routedId(syntheticId)
            .streamId(streamId)
            .sequence(Long.MAX_VALUE)
            .acknowledge(0L)
            .maximum(0)
            .build();

        stream.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private MessageConsumer supplyReplyTo(
        long streamId)
    {
        final int index = streamIndex(streamId);
        return writersByIndex.computeIfAbsent(index, supplyWriter);
    }

    private MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final FrameFW frame = frameRO.wrap(buffer, index, length);
        final long streamId = frame.streamId();
        assert StreamId.isInitial(streamId);
        throttles[throttleIndex(streamId)].put(instanceId(streamId), sender);
        final long replyId = supplyReplyId(streamId);
        correlations.put(replyId, sender);
        return supplyReceiver(streamId);
    }

    @Override
    public MessageConsumer supplySender(
        long streamId)
    {
        final int clientIndex = clientIndex(streamId);
        return writersByIndex.computeIfAbsent(clientIndex, supplyWriter);
    }

    @Override
    public MessageConsumer supplyReceiver(
        long streamId)
    {
        final int remoteIndex = serverIndex(streamId);
        return writersByIndex.computeIfAbsent(remoteIndex, supplyWriter);
    }

    public int readEvent(
        MessageConsumer handler,
        int messageCountLimit)
    {
        return eventsLayout.readEvent(handler, messageCountLimit);
    }

    public int peekEvent(
        MessageConsumer handler)
    {
        return eventsLayout.peekEvent(handler);
    }

    public MessageReader supplyEventReader()
    {
        return supplyEventReader.get();
    }

    public EventFormatter supplyEventFormatter()
    {
        return this.eventFormatter;
    }

    private MessageConsumer supplyWriter(
        int index)
    {
        return supplyTarget(index).writeHandler();
    }

    private Target supplyTarget(
        int index)
    {
        return targetsByIndex.computeIfAbsent(index, newTarget);
    }

    private LongConsumer supplyMetricWriter(
        Metric.Kind kind,
        long bindingId,
        long metricId)
    {
        return metricWriterSuppliers.get(kind).apply(bindingId, metricId);
    }

    private MessageConsumer supplyMetricRecorder(
        long bindingId,
        MetricHandlerKind kind,
        MetricContext.Direction direction)
    {
        MessageConsumer recorder = MessageConsumer.NOOP;
        BindingRegistry binding = registry.resolveBinding(bindingId);
        if (binding != null)
        {
            if (kind == ROUTED)
            {
                recorder = resolveRoutedMetricRecorder(binding, direction);
            }
            else if (kind == ORIGIN)
            {
                recorder = resolveOriginMetricRecorder(binding, direction);
            }
        }
        return recorder;
    }

    private MessageConsumer resolveRoutedMetricRecorder(
        BindingRegistry binding,
        MetricContext.Direction direction)
    {
        MessageConsumer recorder = MessageConsumer.NOOP;
        if (direction == RECEIVED)
        {
            recorder = binding.receivedRoutedMetricHandler();
        }
        else if (direction == SENT)
        {
            recorder = binding.sentRoutedMetricHandler();
        }
        return recorder;
    }

    private MessageConsumer resolveOriginMetricRecorder(
        BindingRegistry binding,
        MetricContext.Direction direction)
    {
        MessageConsumer recorder = MessageConsumer.NOOP;
        if (direction == RECEIVED)
        {
            recorder = binding.receivedOriginMetricHandler();
        }
        else if (direction == SENT)
        {
            recorder = binding.sentOriginMetricHandler();
        }
        return recorder;
    }

    private Target newTarget(
        int index)
    {
        return new Target(config, index, writeBuffer, correlations, streams, streamSets, throttles);
    }

    private DefaultBudgetDebitor newBudgetDebitor(
        int ownerIndex)
    {
        final BudgetsLayout layout = new BudgetsLayout.Builder()
                .path(config.directory().resolve(String.format("budgets%d", ownerIndex)))
                .owner(false)
                .build();

        return new DefaultBudgetDebitor(localIndex, ownerIndex, layout);
    }

    private int resolveRemoteIndex(
        long bindingId)
    {
        final Affinity affinity = supplyAffinity(bindingId);
        final BitSet mask = affinity.mask;
        final int remoteIndex = affinity.nextIndex;

        // currently round-robin with prefer-local only
        assert mask.cardinality() != 0;
        if (remoteIndex != localIndex)
        {
            int nextIndex = affinity.mask.nextSetBit(remoteIndex + 1);
            if (nextIndex == -1)
            {
                nextIndex = affinity.mask.nextSetBit(0);
            }
            affinity.nextIndex = nextIndex;
        }

        return remoteIndex;
    }

    private Affinity supplyAffinity(
        long bindingId)
    {
        return affinityByBindingId.computeIfAbsent(bindingId, resolveAffinity);
    }

    public Affinity resolveAffinity(
        long bindingId)
    {
        long mask = affinityMask.applyAsLong(bindingId);

        if (Long.bitCount(mask) == 0)
        {
            int namespaceId = NamespacedId.namespaceId(bindingId);
            int localId = NamespacedId.localId(bindingId);
            String namespace = labels.lookupLabel(namespaceId);
            String binding = labels.lookupLabel(localId);
            throw new IllegalStateException(String.format("affinity mask must specify at least one bit: %s.%s %d",
                    namespace, binding, mask));
        }

        Affinity affinity = new Affinity();
        affinity.mask = BitSet.valueOf(new long[] { mask });
        affinity.nextIndex = affinity.mask.get(localIndex) ? localIndex : affinity.mask.nextSetBit(0);

        return affinity;
    }

    private static SignalFW.Builder newSignalRW(
        int capacity)
    {
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[capacity]);
        return new SignalFW.Builder().wrap(buffer, 0, buffer.capacity());
    }

    private Int2ObjectHashMap<MessageConsumer>[] initDispatcher()
    {
        @SuppressWarnings("unchecked")
        Int2ObjectHashMap<MessageConsumer>[] dispatcher = new Int2ObjectHashMap[64];
        for (int i = 0; i < dispatcher.length; i++)
        {
            dispatcher[i] = new Int2ObjectHashMap<>();
        }
        return dispatcher;
    }

    private final class ElektronSignaler implements Signaler
    {
        private final ThreadLocal<SignalFW.Builder> signalRW;

        private final ExecutorService executorService;

        private long nextFutureId;

        private ElektronSignaler(
            ExecutorService executorService,
            int slotCapacity)
        {
            this.executorService = executorService;
            signalRW = withInitial(() -> newSignalRW(slotCapacity));
        }

        public void executeTaskAt(
            long timeMillis,
            Runnable task)
        {
            final long timerId = timerWheel.scheduleTimer(timeMillis);
            final Runnable oldTask = tasksByTimerId.put(timerId, task);
            assert oldTask == null;
            assert timerId >= 0L;
        }

        @Override
        public long signalAt(
            long timeMillis,
            int signalId,
            IntConsumer handler)
        {
            final long timerId = timerWheel.scheduleTimer(timeMillis);
            final Runnable task = () -> handler.accept(signalId);
            final Runnable oldTask = tasksByTimerId.put(timerId, task);
            assert oldTask == null;
            assert timerId >= 0L;
            return timerId;
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
            final long timerId = timerWheel.scheduleTimer(timeMillis);
            final Runnable task = () -> signal(originId, routedId, streamId, 0L, 0L,
                traceId, NO_CANCEL_ID, signalId, contextId);
            final Runnable oldTask = tasksByTimerId.put(timerId, task);
            assert oldTask == null;
            assert timerId >= 0L;
            return timerId;
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
            long cancelId;

            if (executorService != null)
            {
                nextFutureId = (nextFutureId + 1) & 0x7fff_ffff_ffff_ffffL;
                final long newFutureId = (nextFutureId << 1) | 0x8000_0000_0000_0001L;
                assert newFutureId != NO_CANCEL_ID;

                final Future<?> newFuture = executorService.submit(
                    () -> invokeAndSignal(task, originId, routedId, streamId, traceId, 0L, 0L, newFutureId, signalId, contextId));
                final Future<?> oldFuture = futuresById.put(newFutureId, newFuture);
                assert oldFuture == null;
                cancelId = newFutureId;
            }
            else
            {
                cancelId = NO_CANCEL_ID;
                invokeAndSignal(task, originId, routedId, streamId, 0L, 0L, traceId, cancelId, signalId, contextId);
            }

            assert cancelId < 0L;

            return cancelId;
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
            boolean cancelled = false;

            if (cancelId > 0L)
            {
                final long timerId = cancelId;
                cancelled = timerWheel.cancelTimer(timerId);
                tasksByTimerId.remove(timerId);
            }
            else if (cancelId != NO_CANCEL_ID)
            {
                final long futureId = cancelId;
                final Future<?> future = futuresById.remove(futureId);
                cancelled = future != null && future.cancel(true);
            }

            return cancelled;
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
            final long timestamp = timestamps ? System.nanoTime() : 0L;

            final SignalFW signal = signalRW.get()
                .rewrap()
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(0)
                .timestamp(timestamp)
                .traceId(traceId)
                .cancelId(cancelId)
                .signalId(signalId)
                .contextId(contextId)
                .build();

            streamsBuffer.write(signal.typeId(), signal.buffer(), signal.offset(), signal.sizeof());
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
            final long timestamp = timestamps ? System.nanoTime() : 0L;

            final SignalFW signal = signalRW.get()
                                            .rewrap()
                                            .originId(originId)
                                            .routedId(routedId)
                                            .streamId(streamId)
                                            .sequence(sequence)
                                            .acknowledge(acknowledge)
                                            .maximum(0)
                                            .timestamp(timestamp)
                                            .traceId(traceId)
                                            .cancelId(cancelId)
                                            .signalId(signalId)
                                            .contextId(contextId)
                                            .payload(buffer, offset, length)
                                            .build();

            streamsBuffer.write(signal.typeId(), signal.buffer(), signal.offset(), signal.sizeof());
        }
    }

    private static class Affinity
    {
        BitSet mask;
        int nextIndex;
    }
}
