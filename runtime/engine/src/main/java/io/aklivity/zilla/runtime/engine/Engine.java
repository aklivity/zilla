/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.CloseHelper;
import org.agrona.ErrorHandler;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.AgentRunner;

import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Info;
import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.registry.DispatchAgent;
import io.aklivity.zilla.runtime.engine.internal.registry.FileWatcherTask;
import io.aklivity.zilla.runtime.engine.internal.registry.RegisterTask;
import io.aklivity.zilla.runtime.engine.internal.registry.UnregisterTask;
import io.aklivity.zilla.runtime.engine.internal.registry.WatcherTask;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;
import io.aklivity.zilla.runtime.engine.vault.Vault;

public final class Engine implements AutoCloseable
{
    private final Collection<Binding> bindings;
    private final ExecutorService tasks;
    private final Collection<AgentRunner> runners;
    private final ToLongFunction<String> counter;
    private final Tuning tuning;
    private final List<EngineExtSpi> extensions;
    private final EngineExtContext context;

    private final AtomicInteger nextTaskId;
    private final ThreadFactory factory;

    private final ToIntFunction<String> supplyLabelId;
    private final RegisterTask registerTask;
    private final UnregisterTask unregisterTask;
    private final WatcherTask watcherTask;

    Engine(
        EngineConfiguration config,
        Collection<Binding> bindings,
        Collection<Guard> guards,
        Collection<Vault> vaults,
        ErrorHandler errorHandler,
        Collection<EngineAffinity> affinities)
    {
        this.nextTaskId = new AtomicInteger();
        this.factory = Executors.defaultThreadFactory();

        ExecutorService tasks = null;
        if (config.taskParallelism() > 0)
        {
            tasks = newFixedThreadPool(config.taskParallelism(), this::newTaskThread);
        }

        int workerCount = config.workers();

        Info info = new Info(config.directory(), workerCount);
        info.reset();

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
            int bindingId = labels.supplyLabelId(affinity.binding);
            long routeId = NamespacedId.id(namespaceId, bindingId);
            tuning.affinity(routeId, affinity.mask);
        }
        this.tuning = tuning;

        Collection<DispatchAgent> dispatchers = new LinkedHashSet<>();
        for (int coreIndex = 0; coreIndex < workerCount; coreIndex++)
        {
            DispatchAgent agent =
                new DispatchAgent(config, tasks, labels, errorHandler, tuning::affinity,
                        bindings, guards, vaults, coreIndex);
            dispatchers.add(agent);
        }

        final Consumer<String> logger = config.verbose() ? System.out::print : m -> {};

        final List<EngineExtSpi> extensions = ServiceLoader.load(EngineExtSpi.class).stream()
                .map(Provider::get)
                .collect(toList());

        final ContextImpl context = new ContextImpl(config, errorHandler, dispatchers);

        final Collection<URL> schemaTypes = new ArrayList<>();
        schemaTypes.addAll(bindings.stream().map(Binding::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(guards.stream().map(Guard::type).filter(Objects::nonNull).collect(toList()));
        schemaTypes.addAll(vaults.stream().map(Vault::type).filter(Objects::nonNull).collect(toList()));

        final Map<String, Guard> guardsByType = guards.stream()
            .collect(Collectors.toMap(g -> g.name(), g -> g));


        registerTask = new RegisterTask(schemaTypes, guardsByType::get,
            labels::supplyLabelId, maxWorkers, tuning, dispatchers, errorHandler, logger, context, config, extensions);
        unregisterTask = new UnregisterTask(dispatchers, context, extensions);

        URL configURL = config.configURL();
        if (configURL == null)
        {
            this.watcherTask = new FileWatcherTask(config.configURL(), this::reconfigure);
        }
        else if ("http".equals(configURL.getProtocol()) || "https".equals(configURL.getProtocol()))
        {
            //TODO: implement watcherTask for HTTP config
            this.watcherTask = new WatcherTask(config.configURL(), this::reconfigure)
            {
                @Override
                public boolean run()
                {
                    initConfigLatch.countDown();
                    throw new UnsupportedOperationException();
                }
            };
        }
        else
        {
            this.watcherTask = new FileWatcherTask(config.configURL(), this::reconfigure);
        }

        List<AgentRunner> runners = new ArrayList<>(dispatchers.size());
        dispatchers.forEach(d -> runners.add(d.runner()));

        final ToLongFunction<String> counter =
            name -> dispatchers.stream().mapToLong(d -> d.counter(name)).sum();

        this.supplyLabelId = labels::supplyLabelId;

        this.bindings = bindings;
        this.tasks = tasks;
        this.extensions = extensions;
        this.context = context;
        this.runners = runners;
        this.counter = counter;
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

    public EngineStats stats(
        String namespace,
        String binding)
    {
        return context.load(namespace, binding);
    }

    public long counter(
        String name)
    {
        return counter.applyAsLong(name);
    }

    public Future<Void> start() throws ExecutionException, InterruptedException
    {
        for (AgentRunner runner : runners)
        {
            AgentRunner.startOnThread(runner, Thread::new);
        }
        commonPool().submit(watcherTask);
        watcherTask.awaitInitConfig();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() throws Exception
    {
        final List<Throwable> errors = new ArrayList<>();

        watcherTask.interrupt();

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

    private void reconfigure(String configText)
    {
        try
        {
            System.out.println("Reconfigure with config: " + configText);
            commonPool().submit(unregisterTask).get();
            registerTask.setConfigText(configText);
            unregisterTask.setRootNamespace(commonPool().submit(registerTask).get());
        }
        catch (InterruptedException | ExecutionException ex)
        {
            rethrowUnchecked(ex);
        }
    }

    public static EngineBuilder builder()
    {
        return new EngineBuilder();
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

    private final class ContextImpl implements EngineExtContext
    {
        private final Configuration config;
        private final ErrorHandler errorHandler;
        private final Collection<DispatchAgent> dispatchers;

        private ContextImpl(
            Configuration config,
            ErrorHandler errorHandler,
            Collection<DispatchAgent> dispatchers)
        {
            this.config = config;
            this.errorHandler = errorHandler;
            this.dispatchers = dispatchers;
        }

        @Override
        public Configuration config()
        {
            return config;
        }

        @Override
        public EngineStats load(
            String namespace,
            String binding)
        {
            int namespaceId = supplyLabelId.applyAsInt(namespace);
            int bindingId = supplyLabelId.applyAsInt(binding);
            long namespacedId = NamespacedId.id(namespaceId, bindingId);

            return new LoadImpl(namespacedId);
        }

        @Override
        public void onError(
            Exception error)
        {
            errorHandler.onError(error);
        }

        private final class LoadImpl implements EngineStats
        {
            private final ToLongFunction<? super DispatchAgent> initialOpens;
            private final ToLongFunction<? super DispatchAgent> initialCloses;
            private final ToLongFunction<? super DispatchAgent> initialErrors;
            private final ToLongFunction<? super DispatchAgent> initialBytes;

            private final ToLongFunction<? super DispatchAgent> replyOpens;
            private final ToLongFunction<? super DispatchAgent> replyCloses;
            private final ToLongFunction<? super DispatchAgent> replyErrors;
            private final ToLongFunction<? super DispatchAgent> replyBytes;

            private LoadImpl(
                long id)
            {
                this.initialOpens = d -> d.initialOpens(id);
                this.initialCloses = d -> d.initialCloses(id);
                this.initialErrors = d -> d.initialErrors(id);
                this.initialBytes = d -> d.initialBytes(id);
                this.replyOpens = d -> d.replyOpens(id);
                this.replyCloses = d -> d.replyCloses(id);
                this.replyErrors = d -> d.replyErrors(id);
                this.replyBytes = d -> d.replyBytes(id);
            }

            @Override
            public long initialOpens()
            {
                return dispatchers.stream().mapToLong(initialOpens).sum();
            }

            @Override
            public long initialCloses()
            {
                return dispatchers.stream().mapToLong(initialCloses).sum();
            }

            @Override
            public long initialBytes()
            {
                return dispatchers.stream().mapToLong(initialBytes).sum();
            }

            @Override
            public long initialErrors()
            {
                return dispatchers.stream().mapToLong(initialErrors).sum();
            }

            @Override
            public long replyOpens()
            {
                return dispatchers.stream().mapToLong(replyOpens).sum();
            }

            @Override
            public long replyCloses()
            {
                return dispatchers.stream().mapToLong(replyCloses).sum();
            }

            @Override
            public long replyBytes()
            {
                return dispatchers.stream().mapToLong(replyBytes).sum();
            }

            @Override
            public long replyErrors()
            {
                return dispatchers.stream().mapToLong(replyErrors).sum();
            }
        }
    }
}
