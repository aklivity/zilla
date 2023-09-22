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
import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.AgentRunner;

import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.catalog.Catalog;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.exporter.Exporter;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Info;
import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.registry.ConfigurationManager;
import io.aklivity.zilla.runtime.engine.internal.registry.DispatchAgent;
import io.aklivity.zilla.runtime.engine.internal.registry.FileWatcherTask;
import io.aklivity.zilla.runtime.engine.internal.registry.HttpWatcherTask;
import io.aklivity.zilla.runtime.engine.internal.registry.WatcherTask;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.metrics.Collector;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.validator.ValidatorFactorySpi;
import io.aklivity.zilla.runtime.engine.vault.Vault;

public final class Engine implements Collector, AutoCloseable
{
    private final Collection<Binding> bindings;
    private final ExecutorService tasks;
    private final Collection<AgentRunner> runners;
    private final Tuning tuning;
    private final List<EngineExtSpi> extensions;
    private final ContextImpl context;

    private final AtomicInteger nextTaskId;
    private final ThreadFactory factory;

    private final ConfigurationManager configurationManager;
    private final WatcherTask watcherTask;
    private final Map<URL, NamespaceConfig> namespaces;
    private final URL rootConfigURL;
    private final Collection<DispatchAgent> dispatchers;
    private final boolean readonly;
    private Future<Void> watcherTaskRef;

    Engine(
        EngineConfiguration config,
        Collection<Binding> bindings,
        Collection<Exporter> exporters,
        Collection<Guard> guards,
        Collection<MetricGroup> metricGroups,
        Collection<Vault> vaults,
        Collection<Catalog> catalogs,
        Collection<ValidatorFactorySpi> validatorFactories,
        ErrorHandler errorHandler,
        Collection<EngineAffinity> affinities,
        boolean readonly)
    {
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

        Collection<DispatchAgent> dispatchers = new LinkedHashSet<>();
        for (int coreIndex = 0; coreIndex < workerCount; coreIndex++)
        {
            DispatchAgent agent =
                new DispatchAgent(config, tasks, labels, errorHandler, tuning::affinity,
                        bindings, exporters, guards, vaults, catalogs, metricGroups, this, coreIndex, readonly);
            dispatchers.add(agent);
        }
        this.dispatchers = dispatchers;

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
        schemaTypes.addAll(validatorFactories.stream().map(ValidatorFactorySpi::schema).filter(Objects::nonNull)
            .collect(toList()));

        final Map<String, Guard> guardsByType = guards.stream()
            .collect(Collectors.toMap(g -> g.name(), g -> g));

        this.rootConfigURL = config.configURL();
        String protocol = rootConfigURL.getProtocol();
        if ("file".equals(protocol) || "jar".equals(protocol))
        {
            Function<String, String> watcherReadURL = l -> readURL(rootConfigURL, l);
            this.watcherTask = new FileWatcherTask(watcherReadURL, this::reconfigure);
        }
        else if ("http".equals(protocol) || "https".equals(protocol))
        {
            this.watcherTask = new HttpWatcherTask(this::reconfigure, config.configPollIntervalSeconds());
        }
        else
        {
            throw new UnsupportedOperationException();
        }

        this.configurationManager = new ConfigurationManager(schemaTypes, guardsByType::get, labels::supplyLabelId, maxWorkers,
            tuning, dispatchers, logger, context, config, extensions, this::readURL);

        this.namespaces = new HashMap<>();

        List<AgentRunner> runners = new ArrayList<>(dispatchers.size());
        dispatchers.forEach(d -> runners.add(d.runner()));

        this.bindings = bindings;
        this.tasks = tasks;
        this.extensions = extensions;
        this.context = context;
        this.runners = runners;
        this.readonly = readonly;
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

    public void start() throws Exception
    {
        for (AgentRunner runner : runners)
        {
            AgentRunner.startOnThread(runner, Thread::new);
        }
        watcherTaskRef = watcherTask.submit();
        if (!readonly)
        {
            // ignore the config file in read-only mode; no config will be read so no namespaces, bindings, etc will be attached
            watcherTask.watch(rootConfigURL).get();
        }
    }

    @Override
    public void close() throws Exception
    {
        final List<Throwable> errors = new ArrayList<>();

        watcherTask.close();
        watcherTaskRef.get();

        for (AgentRunner runner : runners)
        {
            try
            {
                CloseHelper.close(runner);
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

    private NamespaceConfig reconfigure(
        URL configURL,
        String configText)
    {
        NamespaceConfig newNamespace = configurationManager.parse(configURL, configText);
        if (newNamespace != null)
        {
            NamespaceConfig oldNamespace = namespaces.get(configURL);
            configurationManager.unregister(oldNamespace);
            try
            {
                configurationManager.register(newNamespace);
                namespaces.put(configURL, newNamespace);
            }
            catch (Exception ex)
            {
                context.onError(ex);
                configurationManager.register(oldNamespace);
                namespaces.put(configURL, oldNamespace);
            }
        }
        return newNamespace;
    }

    public static EngineBuilder builder()
    {
        return new EngineBuilder();
    }

    private String readURL(
        URL configURL,
        String location)
    {
        String output = null;
        try
        {
            final URL fileURL = new URL(configURL, location);
            if ("http".equals(fileURL.getProtocol()) || "https".equals(fileURL.getProtocol()))
            {
                HttpClient client = HttpClient.newBuilder()
                    .version(HTTP_2)
                    .followRedirects(NORMAL)
                    .build();

                HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(fileURL.toURI())
                    .build();

                HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

                output = response.body();
            }
            else
            {

                URLConnection connection = fileURL.openConnection();
                try (InputStream input = connection.getInputStream())
                {
                    output = new String(input.readAllBytes(), UTF_8);
                }
            }
        }
        catch (IOException | URISyntaxException | InterruptedException ex)
        {
            output = "";
        }
        return output;
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
        for (DispatchAgent dispatchAgent : dispatchers)
        {
            LongSupplier counterReader = dispatchAgent.supplyCounter(bindingId, metricId);
            result += counterReader.getAsLong();
        }
        return result;
    }

    // required for testing
    public LongConsumer counterWriter(
        long bindingId,
        long metricId,
        int core)
    {
        DispatchAgent dispatcher = dispatchers.toArray(DispatchAgent[]::new)[core];
        return dispatcher.supplyCounterWriter(bindingId, metricId);
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
        for (DispatchAgent dispatchAgent : dispatchers)
        {
            LongSupplier counterReader = dispatchAgent.supplyGauge(bindingId, metricId);
            result += counterReader.getAsLong();
        }
        return result;
    }

    // required for testing
    public LongConsumer gaugeWriter(
        long bindingId,
        long metricId,
        int core)
    {
        DispatchAgent dispatcher = dispatchers.toArray(DispatchAgent[]::new)[core];
        return dispatcher.supplyGaugeWriter(bindingId, metricId);
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
        for (DispatchAgent dispatchAgent : dispatchers)
        {
            LongSupplier[] readers = dispatchAgent.supplyHistogram(bindingId, metricId);
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
        DispatchAgent dispatcher = dispatchers.toArray(DispatchAgent[]::new)[core];
        return dispatcher.supplyHistogramWriter(bindingId, metricId);
    }

    @Override
    public long[][] counterIds()
    {
        // the list of counter ids are expected to be identical in all cores
        DispatchAgent dispatchAgent = dispatchers.iterator().next();
        return dispatchAgent.counterIds();
    }

    @Override
    public long[][] gaugeIds()
    {
        // the list of gauge ids are expected to be identical in all cores
        DispatchAgent dispatchAgent = dispatchers.iterator().next();
        return dispatchAgent.gaugeIds();
    }

    @Override
    public long[][] histogramIds()
    {
        // the list of histogram ids are expected to be identical in all cores
        DispatchAgent dispatchAgent = dispatchers.iterator().next();
        return dispatchAgent.histogramIds();
    }

    public String supplyLocalName(
        long namespacedId)
    {
        DispatchAgent dispatchAgent = dispatchers.iterator().next();
        return dispatchAgent.supplyLocalName(namespacedId);
    }

    public int supplyLabelId(
        String label)
    {
        DispatchAgent dispatchAgent = dispatchers.iterator().next();
        return dispatchAgent.supplyTypeId(label);
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
