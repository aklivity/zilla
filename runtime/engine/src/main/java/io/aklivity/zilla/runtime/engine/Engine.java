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
package io.aklivity.zilla.runtime.engine;

import static io.aklivity.zilla.runtime.engine.internal.layouts.metrics.HistogramsLayout.BUCKETS;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.agrona.ErrorHandler;
import org.agrona.collections.Int2ObjectHashMap;

import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.event.EventFormatterFactory;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Info;
import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.layouts.EventsLayout;
import io.aklivity.zilla.runtime.engine.internal.registry.EngineManager;
import io.aklivity.zilla.runtime.engine.internal.registry.EngineWorker;
import io.aklivity.zilla.runtime.engine.internal.types.event.EventFW;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.model.Model;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.vault.Vault;

public final class Engine implements Collector, AutoCloseable
{
    private final Collection<Binding> bindings;
    private final ExecutorService tasks;
    private final Tuning tuning;
    private final List<EngineExtSpi> extensions;
    private final ContextImpl context;

    private final AtomicInteger nextTaskId;
    private final ThreadFactory factory;

    private final Path configPath;
    private final List<EngineWorker> workers;
    private final boolean readonly;
    private final EngineConfiguration config;
    private final EngineManager manager;

    Engine(
        EngineConfiguration config,
        Collection<Binding> bindings,
        Collection<Exporter> exporters,
        Collection<Guard> guards,
        Collection<MetricGroup> metricGroups,
        Collection<Vault> vaults,
        Collection<Catalog> catalogs,
        Collection<Model> models,
        EventFormatterFactory eventFormatterFactory,
        ErrorHandler errorHandler,
        Collection<EngineAffinity> affinities,
        boolean readonly)
    {
        this.config = config;
        this.nextTaskId = new AtomicInteger();
        this.factory = Executors.defaultThreadFactory();

        ExecutorService tasks = null;
        if (config.taskParallelism() > 0)
        {
            tasks = newFixedThreadPool(config.taskParallelism(), this::newTaskThread);
        }

        Info info = new Info.Builder()
            .path(config.directory())
            .workerCount(config.workers())
            .readonly(readonly)
            .build();
        int workerCount = info.workerCount();

        LabelManager labels = new LabelManager(config.directory());
        Int2ObjectHashMap<ToIntFunction<KindConfig>> maxWorkersByBindingType = new Int2ObjectHashMap<>();

        // ensure parity with external labelIds
        for (EngineAffinity affinity : affinities)
        {
            labels.supplyLabelId(affinity.namespace);
            labels.supplyLabelId(affinity.binding);
        }

        for (Binding binding : bindings)
        {
            String name = binding.name();
            maxWorkersByBindingType.put(name.intern().hashCode(), binding::workers);
        }
        IntFunction<ToIntFunction<KindConfig>> maxWorkers = maxWorkersByBindingType::get;

        Tuning tuning = new Tuning(config.directory(), workerCount);
        tuning.reset();
        for (EngineAffinity affinity : affinities)
        {
            int namespaceId = labels.supplyLabelId(affinity.namespace);
            int localId = labels.supplyLabelId(affinity.binding);
            long bindingId = NamespacedId.id(namespaceId, localId);
            tuning.affinity(bindingId, affinity.mask);
        }
        this.tuning = tuning;

        List<EngineWorker> workers = new ArrayList<>(workerCount);
        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++)
        {
            EngineWorker worker =
                new EngineWorker(config, tasks, labels, errorHandler, tuning::affinity, this::resolvePath, bindings, exporters,
                    guards, vaults, catalogs, models, metricGroups, this, this::supplyEventReader,
                    eventFormatterFactory, workerIndex, readonly, this::process);
            workers.add(worker);
        }
        this.workers = workers;

        final Consumer<String> logger = config.verbose() ? System.out::println : m -> {};

        final List<EngineExtSpi> extensions = ServiceLoader.load(EngineExtSpi.class).stream()
                .map(Provider::get)
                .collect(toList());

        final ContextImpl context = new ContextImpl(config, errorHandler, labels::supplyLabelId);

        final Collection<URL> schemaTypes = new ArrayList<>();
        schemaTypes.addAll(bindings.stream().map(Binding::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(exporters.stream().map(Exporter::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(guards.stream().map(Guard::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(metricGroups.stream().map(MetricGroup::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(vaults.stream().map(Vault::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(catalogs.stream().map(Catalog::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(models.stream().map(Model::type).filter(Objects::nonNull).collect(toList()));

        final Map<String, Binding> bindingsByType = bindings.stream()
            .collect(Collectors.toMap(b -> b.name(), b -> b));
        final Map<String, Guard> guardsByType = guards.stream()
            .collect(Collectors.toMap(g -> g.name(), g -> g));

        this.configPath = config.configPath();
        EngineManager manager = new EngineManager(
            schemaTypes,
            bindingsByType::get,
            guardsByType::get,
            labels::supplyLabelId,
            labels::lookupLabel,
            maxWorkers,
            tuning,
            workers,
            logger,
            context,
            config,
            extensions,
            this.configPath,
            this::readPath,
            this::readLocation);

        this.bindings = bindings;
        this.tasks = tasks;
        this.extensions = extensions;
        this.context = context;
        this.readonly = readonly;
        this.manager = manager;
    }

    public <T> T binding(
        Class<T> kind)
    {
        return bindings.stream()
                .filter(kind::isInstance)
                .map(kind::cast)
                .findFirst()
                .orElse(null);
    }

    private void process(
        NamespaceConfig config)
    {
        manager.process(config);
    }

    public void start() throws Exception
    {
        for (EngineWorker worker : workers)
        {
            worker.doStart();
        }

        // ignore the config file in read-only mode; no config will be read so no namespaces, bindings, etc. will be attached
        if (!readonly)
        {
            manager.start();
        }
    }

    @Override
    public void close() throws Exception
    {
        if (config.drainOnClose())
        {
            workers.forEach(EngineWorker::drain);
        }

        final List<Throwable> errors = new ArrayList<>();

        manager.close();

        for (EngineWorker worker : workers)
        {
            try
            {
                worker.doClose();
            }
            catch (Throwable ex)
            {
                errors.add(ex);
            }
        }

        if (tasks != null)
        {
            tasks.shutdownNow();
        }

        tuning.close();

        extensions.forEach(e -> e.onUnregistered(context));

        if (!errors.isEmpty())
        {
            final Throwable t = errors.get(0);
            errors.stream().filter(x -> x != t).forEach(x -> t.addSuppressed(x));
            rethrowUnchecked(t);
        }
    }

    // required for testing
    public ContextImpl context()
    {
        return context;
    }

    public static EngineBuilder builder()
    {
        return new EngineBuilder();
    }

    private String readPath(
        Path path)
    {
        String result;
        try
        {
            System.out.println("ENG readPath path " + path); // TODO: Ati
            result = Files.readString(path);
            System.out.println("ENG readPath result [" + result + "]"); // TODO: Ati
        }
        catch (Exception ex)
        {
            result = "";
        }
        return result;
    }

    public Path resolvePath(
        String location)
    {
        return configPath.resolveSibling(location);
    }

    private String readLocation(
        String location)
    {
        return readPath(resolvePath(location));
    }

    private Thread newTaskThread(
        Runnable r)
    {
        Thread t = factory.newThread(r);

        if (t != null)
        {
            t.setName(String.format("engine/task#%d", nextTaskId.getAndIncrement()));
        }

        return t;
    }

    @Override
    public LongSupplier counter(
        long bindingId,
        long metricId)
    {
        return () -> aggregateCounterValue(bindingId, metricId);
    }

    private long aggregateCounterValue(
        long bindingId,
        long metricId)
    {
        long result = 0;
        for (EngineWorker worker : workers)
        {
            LongSupplier reader = worker.supplyCounter(bindingId, metricId);
            result += reader.getAsLong();
        }
        return result;
    }

    // required for testing
    public LongConsumer counterWriter(
        long bindingId,
        long metricId,
        int core)
    {
        EngineWorker worker = workers.toArray(EngineWorker[]::new)[core];
        return worker.supplyCounterWriter(bindingId, metricId);
    }

    @Override
    public LongSupplier gauge(
        long bindingId,
        long metricId)
    {
        return () -> aggregateGaugeValue(bindingId, metricId);
    }

    private long aggregateGaugeValue(
        long bindingId,
        long metricId)
    {
        long result = 0;
        for (EngineWorker worker : workers)
        {
            LongSupplier reader = worker.supplyGauge(bindingId, metricId);
            result += reader.getAsLong();
        }
        return result;
    }

    // required for testing
    public LongConsumer gaugeWriter(
        long bindingId,
        long metricId,
        int core)
    {
        EngineWorker worker = workers.get(core);
        return worker.supplyGaugeWriter(bindingId, metricId);
    }

    @Override
    public LongSupplier[] histogram(
        long bindingId,
        long metricId)
    {
        return createHistogramReaders(bindingId, metricId);
    }

    private LongSupplier[] createHistogramReaders(
        long bindingId,
        long metricId)
    {
        LongSupplier[] result = new LongSupplier[BUCKETS];
        for (int i = 0; i < BUCKETS; i++)
        {
            final int index = i;
            result[index] = () -> aggregateHistogramBucketValue(bindingId, metricId, index);
        }
        return result;
    }

    private long aggregateHistogramBucketValue(
        long bindingId,
        long metricId,
        int index)
    {
        long result = 0L;
        for (EngineWorker worker : workers)
        {
            LongSupplier[] readers = worker.supplyHistogram(bindingId, metricId);
            result += readers[index].getAsLong();
        }
        return result;
    }

    // required for testing
    public LongConsumer histogramWriter(
        long bindingId,
        long metricId,
        int core)
    {
        EngineWorker worker = workers.get(core);
        return worker.supplyHistogramWriter(bindingId, metricId);
    }

    @Override
    public long[][] counterIds()
    {
        // the list of counter ids are expected to be identical in all cores
        EngineWorker worker = workers.get(0);
        return worker.counterIds();
    }

    @Override
    public long[][] gaugeIds()
    {
        // the list of gauge ids are expected to be identical in all cores
        EngineWorker worker = workers.get(0);
        return worker.gaugeIds();
    }

    @Override
    public long[][] histogramIds()
    {
        // the list of histogram ids are expected to be identical in all cores
        EngineWorker worker = workers.get(0);
        return worker.histogramIds();
    }

    public MessageReader supplyEventReader()
    {
        return new EventReader();
    }

    public String supplyLocalName(
        long namespacedId)
    {
        EngineWorker worker = workers.get(0);
        return worker.supplyLocalName(namespacedId);
    }

    public int supplyLabelId(
        String label)
    {
        EngineWorker worker = workers.get(0);
        return worker.supplyTypeId(label);
    }

    private final class EventReader implements MessageReader
    {
        private final EventsLayout.EventAccessor[] accessors;
        private final EventFW eventRO = new EventFW();
        private int minWorkerIndex;
        private long minTimeStamp;

        EventReader()
        {
            accessors = new EventsLayout.EventAccessor[workers.size()];
            for (int i = 0; i < workers.size(); i++)
            {
                accessors[i] = workers.get(i).createEventAccessor();
            }
        }

        @Override
        public int read(
            MessageConsumer handler,
            int messageCountLimit)
        {
            int messagesRead = 0;
            boolean empty = false;
            while (!empty && messagesRead < messageCountLimit)
            {
                int eventCount = 0;
                minWorkerIndex = 0;
                minTimeStamp = Long.MAX_VALUE;
                for (int j = 0; j < workers.size(); j++)
                {
                    final int workerIndex = j;
                    int eventPeeked = accessors[workerIndex].peekEvent((m, b, i, l) ->
                    {
                        eventRO.wrap(b, i, i + l);
                        if (eventRO.timestamp() < minTimeStamp)
                        {
                            minTimeStamp = eventRO.timestamp();
                            minWorkerIndex = workerIndex;
                        }
                    });
                    eventCount += eventPeeked;
                }
                empty = eventCount == 0;
                if (!empty)
                {
                    messagesRead += accessors[minWorkerIndex].readEvent(handler, 1);
                }
            }
            return messagesRead;
        }
    }

    // visible for testing
    public final class ContextImpl implements EngineExtContext
    {
        private final Configuration config;
        private final ErrorHandler errorHandler;
        private final ToIntFunction<String> supplyLabelId;

        private ContextImpl(
            Configuration config,
            ErrorHandler errorHandler,
            ToIntFunction<String> supplyLabelId)
        {
            this.config = config;
            this.errorHandler = errorHandler;
            this.supplyLabelId = supplyLabelId;
        }

        @Override
        public Configuration config()
        {
            return config;
        }

        @Override
        public void onError(
            Exception error)
        {
            errorHandler.onError(error);
        }

        @Override
        public LongSupplier counter(
            String namespace,
            String binding,
            String metric)
        {
            int namespaceId = supplyLabelId.applyAsInt(namespace);
            int bindingId = supplyLabelId.applyAsInt(binding);
            int metricId = supplyLabelId.applyAsInt(metric);
            long namespacedBindingId = NamespacedId.id(namespaceId, bindingId);
            long namespacedMetricId = NamespacedId.id(namespaceId, metricId);
            return Engine.this.counter(namespacedBindingId, namespacedMetricId);
        }

        // required for testing
        public LongConsumer counterWriter(
            String namespace,
            String binding,
            String metric,
            int core)
        {
            int namespaceId = supplyLabelId.applyAsInt(namespace);
            int bindingId = supplyLabelId.applyAsInt(binding);
            int metricId = supplyLabelId.applyAsInt(metric);
            long namespacedBindingId = NamespacedId.id(namespaceId, bindingId);
            long namespacedMetricId = NamespacedId.id(namespaceId, metricId);
            return Engine.this.counterWriter(namespacedBindingId, namespacedMetricId, core);
        }
    }
}
