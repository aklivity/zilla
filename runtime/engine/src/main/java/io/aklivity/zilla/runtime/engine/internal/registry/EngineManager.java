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

import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigException;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;
import io.aklivity.zilla.runtime.engine.config.EngineConfigReader;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;
import io.aklivity.zilla.runtime.engine.internal.watcher.EngineConfigWatcher;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.resolver.Resolver;

public class EngineManager
{
    private static final String CONFIG_TEXT_DEFAULT = "name: default\n";

    private final Collection<URL> schemaTypes;
    private final Function<String, Binding> bindingByType;
    private final Function<String, Guard> guardByType;
    private final ToIntFunction<String> supplyId;
    private final IntFunction<String> supplyName;
    private final IntFunction<ToIntFunction<KindConfig>> maxWorkers;
    private final Tuning tuning;
    private final Collection<EngineWorker> workers;
    private final Consumer<String> logger;
    private final EngineExtContext context;
    private final EngineConfiguration config;
    private final List<EngineExtSpi> extensions;
    private final Function<String, String> readURL;
    private final Resolver expressions;
    private final EngineConfigWatcher watcher;

    private EngineConfig current;

    public EngineManager(
        Collection<URL> schemaTypes,
        Function<String, Binding> bindingByType,
        Function<String, Guard> guardByType,
        ToIntFunction<String> supplyId,
        IntFunction<String> supplyName,
        IntFunction<ToIntFunction<KindConfig>> maxWorkers,
        Tuning tuning,
        Collection<EngineWorker> workers,
        Consumer<String> logger,
        EngineExtContext context,
        EngineConfiguration config,
        List<EngineExtSpi> extensions,
        URL configURL,
        Function<String, String> readURL)
    {
        this.schemaTypes = schemaTypes;
        this.bindingByType = bindingByType;
        this.guardByType = guardByType;
        this.supplyId = supplyId;
        this.supplyName = supplyName;
        this.maxWorkers = maxWorkers;
        this.tuning = tuning;
        this.workers = workers;
        this.logger = logger;
        this.context = context;
        this.config = config;
        this.extensions = extensions;
        this.readURL = readURL;
        this.expressions = Resolver.instantiate(config);
        this.watcher = new EngineConfigWatcher(configURL, readURL, this::reconfigure, this::reloadNamespacesWithChangedResources);
    }

    public void process(
        NamespaceConfig namespace)
    {
        final List<GuardConfig> guards = current.namespaces.stream()
            .map(n -> n.guards)
            .flatMap(gs -> gs.stream())
            .collect(toList());

        process(guards, namespace);
    }

    public void start() throws Exception
    {
        watcher.startWatchingConfig();
    }

    public void close()
    {
        watcher.close();
    }

    private EngineConfig reconfigure(
        String configText)
    {
        EngineConfig newConfig = null;

        try
        {
            newConfig = parse(configText);
            if (newConfig != null)
            {
                final EngineConfig oldConfig = current;
                EngineConfig newConfig0 = newConfig;
                Predicate<String> identical = name ->
                {
                    String hash1 = oldConfig == null ? null : oldConfig.hash(name);
                    String hash2 = newConfig0.hash(name);
                    return hash1 != null && hash1.equals(hash2);
                };
                unregister(oldConfig, identical);

                try
                {
                    current = newConfig;
                    register(newConfig, identical);
                }
                catch (Exception ex)
                {
                    context.onError(ex);

                    current = oldConfig;
                    register(oldConfig, identical);

                    rethrowUnchecked(ex);
                }
            }
        }
        catch (Exception ex)
        {
            logger.accept(ex.getMessage());
            Arrays.stream(ex.getSuppressed())
                .map(Throwable::getMessage)
                .forEach(logger);

            if (current == null)
            {
                throw new ConfigException("Engine configuration failed", ex);
            }
        }

        return newConfig;
    }

    private void reloadNamespacesWithChangedResources(
        Set<String> namespaces)
    {
        if (namespaces != null && !namespaces.isEmpty())
        {
            Set<String> namespaces0 = new HashSet<>(namespaces);
            Predicate<String> identical = name -> !namespaces0.contains(name);
            unregister(current, identical);
            register(current, identical);
        }
    }

    private EngineConfig parse(
        String configText)
    {
        EngineConfig engine = null;

        if (configText == null || configText.isEmpty())
        {
            configText = CONFIG_TEXT_DEFAULT;
        }

        logger.accept(configText);

        try
        {
            EngineConfigReader reader = new EngineConfigReader(
                config,
                new NamespaceConfigAdapterContext(readURL),
                expressions,
                schemaTypes,
                watcher,
                logger);

            engine = reader.read(configText);

            final List<GuardConfig> guards = engine.namespaces.stream()
                .map(n -> n.guards)
                .flatMap(gs -> gs.stream())
                .collect(toList());

            for (NamespaceConfig namespace : engine.namespaces)
            {
                namespace.readURL = readURL;
                process(guards, namespace);
            }
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return engine;
    }

    private void process(
        List<GuardConfig> guards,
        NamespaceConfig namespace)
    {
        assert namespace.readURL != null;

        namespace.id = supplyId.applyAsInt(namespace.name);

        NameResolver resolver = new NameResolver(namespace.id);

        for (GuardConfig guard : namespace.guards)
        {
            guard.id = resolver.resolve(guard.name);
            guard.readURL = namespace.readURL;
        }

        for (VaultConfig vault : namespace.vaults)
        {
            vault.id = resolver.resolve(vault.name);
        }

        for (CatalogConfig catalog : namespace.catalogs)
        {
            catalog.id = resolver.resolve(catalog.name);
        }

        for (MetricConfig metric : namespace.telemetry.metrics)
        {
            metric.id = resolver.resolve(metric.name);
        }

        for (ExporterConfig exporter : namespace.telemetry.exporters)
        {
            exporter.id = resolver.resolve(exporter.name);
            if (exporter.vault != null)
            {
                exporter.vaultId = resolver.resolve(exporter.vault);
            }
        }

        for (BindingConfig binding : namespace.bindings)
        {
            binding.id = resolver.resolve(binding.name);
            binding.entryId = resolver.resolve(binding.entry);
            binding.resolveId = resolver::resolve;
            binding.readURL = namespace.readURL;

            binding.typeId = supplyId.applyAsInt(binding.type);
            binding.kindId = supplyId.applyAsInt(binding.kind.name().toLowerCase());

            Binding typed = bindingByType.apply(binding.type);
            String originType = typed.originType(binding.kind);
            String routedType = typed.routedType(binding.kind);
            binding.originTypeId = originType != null ? supplyId.applyAsInt(originType) : 0L;
            binding.routedTypeId = routedType != null ? supplyId.applyAsInt(routedType) : 0L;

            if (binding.vault != null)
            {
                binding.vaultId = resolver.resolve(binding.vault);
                binding.qvault = resolver.format(binding.vaultId);
            }

            if (binding.catalogs != null)
            {
                for (CatalogedConfig cataloged : binding.catalogs)
                {
                    cataloged.id = resolver.resolve(cataloged.name);
                }
            }

            if (binding.options != null)
            {
                for (ModelConfig model : binding.options.models)
                {
                    if (model.cataloged != null)
                    {
                        for (CatalogedConfig cataloged : model.cataloged)
                        {
                            cataloged.id = resolver.resolve(cataloged.name);
                        }
                    }
                }
            }

            for (RouteConfig route : binding.routes)
            {
                route.id = resolver.resolve(route.exit);
                route.authorized = session -> true;

                if (route.guarded != null)
                {
                    for (GuardedConfig guarded : route.guarded)
                    {
                        guarded.id = resolver.resolve(guarded.name);

                        LongPredicate authorizer = guards.stream()
                            .filter(g -> g.id == guarded.id)
                            .findFirst()
                            .map(g -> guardByType.apply(g.type))
                            .map(g -> g.verifier(EngineWorker::indexOfId, guarded))
                            .orElse(session -> false);

                        LongFunction<String> identifier = guards.stream()
                            .filter(g -> g.id == guarded.id)
                            .findFirst()
                            .map(g -> guardByType.apply(g.type))
                            .map(g -> g.identifier(EngineWorker::indexOfId, guarded))
                            .orElse(session -> null);

                        guarded.identity = identifier;
                        guarded.qname = resolver.format(guarded.id);

                        route.authorized = route.authorized.and(authorizer);
                    }
                }
            }

            Set<Long> metricIds = new HashSet<>();
            TelemetryRefConfig telemetryRef = binding.telemetryRef;
            if (telemetryRef != null)
            {
                if (telemetryRef.metricRefs != null)
                {
                    for (MetricRefConfig metricRef : telemetryRef.metricRefs)
                    {
                        Pattern pattern = Pattern.compile(metricRef.name);
                        for (MetricConfig metric : namespace.telemetry.metrics)
                        {
                            if (pattern.matcher(metric.name).matches())
                            {
                                metricIds.add(resolver.resolve(metric.name));
                            }
                        }
                    }
                }
            }
            binding.metricIds = metricIds.stream().mapToLong(Long::longValue).toArray();

            long affinity = tuning.affinity(binding.id);

            final long maxbits = maxWorkers.apply(binding.type.intern().hashCode()).applyAsInt(binding.kind);
            for (int bitindex = 0; Long.bitCount(affinity) > maxbits; bitindex++)
            {
                affinity &= ~(1 << bitindex);
            }

            tuning.affinity(binding.id, affinity);
        }
    }

    private void register(
        EngineConfig config,
        Predicate<String> identical)
    {
        if (config != null)
        {
            for (NamespaceConfig namespace : config.namespaces)
            {
                if (!identical.test(namespace.name))
                {
                    System.out.println("register: " + namespace.name); // TODO: Ati
                    watcher.addResources(namespace.resources, namespace.name);
                    register(namespace);
                }
            }
        }

        extensions.forEach(e -> e.onRegistered(context));
    }

    private void unregister(
        EngineConfig config,
        Predicate<String> identical)
    {
        if (config != null)
        {
            for (NamespaceConfig namespace : config.namespaces)
            {
                if (!identical.test(namespace.name))
                {
                    System.out.println("unregister: " + namespace.name); // TODO: Ati
                    unregister(namespace);
                    watcher.removeNamespace(namespace.name);
                }
            }
        }

        extensions.forEach(e -> e.onUnregistered(context));
    }

    private void register(
        NamespaceConfig namespace)
    {
        workers.stream()
            .map(w -> w.attach(namespace))
            .reduce(CompletableFuture::allOf)
            .ifPresent(CompletableFuture::join);
    }

    private void unregister(
        NamespaceConfig namespace)
    {
        if (namespace != null)
        {
            workers.stream()
                .map(w -> w.detach(namespace))
                .reduce(CompletableFuture::allOf)
                .ifPresent(CompletableFuture::join);
        }
    }

    private final class NameResolver
    {
        private final int namespaceId;
        private final ThreadLocal<Matcher> matchName;

        private NameResolver(
            int namespaceId)
        {
            this.namespaceId = namespaceId;
            this.matchName = ThreadLocal.withInitial(() -> NamespaceAdapter.PATTERN_NAME.matcher(""));
        }

        private long resolve(
            String name)
        {
            long id = 0L;
            Matcher matchName = this.matchName.get();
            if (name != null && matchName.reset(name).matches())
            {
                String ns = matchName.group("namespace");
                String n = matchName.group("name");

                int nsid = ns != null ? supplyId.applyAsInt(ns) : namespaceId;
                int nid = supplyId.applyAsInt(n);

                id = NamespacedId.id(nsid, nid);
            }
            return id;
        }

        private String format(
            long namespacedId)
        {
            return String.format("%s:%s",
                    supplyName.apply(NamespacedId.namespaceId(namespacedId)),
                    supplyName.apply(NamespacedId.localId(namespacedId)));
        }
    }

    private static final class NamespaceConfigAdapterContext implements ConfigAdapterContext
    {
        private final Function<String, String> readURL;

        NamespaceConfigAdapterContext(
            Function<String, String> readURL)
        {
            this.readURL = readURL;
        }

        @Override
        public String readURL(
            String location)
        {
            return readURL.apply(location);
        }
    }
}
