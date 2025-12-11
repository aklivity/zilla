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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.spi.JsonProvider;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigException;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;
import io.aklivity.zilla.runtime.engine.config.EngineConfigReader;
import io.aklivity.zilla.runtime.engine.config.EngineConfigWriter;
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
import io.aklivity.zilla.runtime.engine.internal.event.EngineEventContext;
import io.aklivity.zilla.runtime.engine.internal.watcher.EngineConfigWatchTask;
import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;
import io.aklivity.zilla.runtime.engine.resolver.Resolver;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectBiFunction;
import io.aklivity.zilla.runtime.engine.util.function.LongObjectPredicate;

public class EngineManager
{
    private static final String CONFIG_TEXT_DEFAULT = "name: default\n";

    private final Collection<URL> schemaTypes;
    private final Collection<URL> systemConfigs;
    private final Function<String, Binding> bindingByType;
    private final Function<String, Guard> guardByType;
    private final ToIntFunction<String> supplyId;
    private final IntFunction<String> supplyName;
    private final IntFunction<ToIntFunction<KindConfig>> maxWorkers;
    private final Tuning tuning;
    private final EngineBoss boss;
    private final Collection<EngineWorker> workers;
    private final Consumer<String> logger;
    private final EngineExtContext context;
    private final EngineConfiguration config;
    private final List<EngineExtSpi> extensions;
    private final Resolver expressions;
    private final Path configPath;
    private final Path localConfigPath;
    private final EngineConfigWatchTask watchTask;
    private final EngineEventContext events;

    private String currentText;
    private EngineConfig current;

    public EngineManager(
        Collection<URL> schemaTypes,
        Collection<URL> systemConfigs,
        Function<String, Binding> bindingByType,
        Function<String, Guard> guardByType,
        ToIntFunction<String> supplyId,
        IntFunction<String> supplyName,
        IntFunction<ToIntFunction<KindConfig>> maxWorkers,
        Tuning tuning,
        EngineBoss boss,
        Collection<EngineWorker> workers,
        Consumer<String> logger,
        EngineExtContext context,
        EngineConfiguration config,
        EngineEventContext events,
        List<EngineExtSpi> extensions)
    {
        this.schemaTypes = schemaTypes;
        this.systemConfigs = systemConfigs;
        this.bindingByType = bindingByType;
        this.guardByType = guardByType;
        this.supplyId = supplyId;
        this.supplyName = supplyName;
        this.maxWorkers = maxWorkers;
        this.tuning = tuning;
        this.boss = boss;
        this.workers = workers;
        this.logger = logger;
        this.context = context;
        this.config = config;
        this.extensions = extensions;
        this.expressions = Resolver.instantiate(config);
        this.configPath = Path.of(config.configURI());
        this.localConfigPath = Optional.ofNullable(config.localConfigURI()).map(Path::of).orElse(null);
        this.watchTask = new WatchTaskImpl(config, events, configPath);
        this.events = events;
    }

    public void start() throws Exception
    {
        extensions.forEach(e -> e.onStart(context));

        watchTask.submit();
        events.started();
    }

    public void close()
    {
        extensions.forEach(e -> e.onClose(context));

        watchTask.close();
        events.stopped();
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

    private void onPathChanged(
        Path watchedPath)
    {
        EngineConfig newConfig = null;

        reconfigure:
        try
        {
            int nonLocalConfigAt = 0;

            String newConfigText = Files.exists(configPath) ? Files.readString(configPath) : null;
            if (localConfigPath != null &&
                Files.exists(localConfigPath))
            {
                String localYamlDoc = Files.readString(localConfigPath);

                nonLocalConfigAt = localYamlDoc.length();

                newConfigText = newConfigText == null ? localYamlDoc : localYamlDoc + "\n" + newConfigText;
            }

            if (newConfigText == null || newConfigText.isEmpty())
            {
                newConfigText = CONFIG_TEXT_DEFAULT;
            }

            String systemYamlDoc = buildSystemNamespaceIfNecessary();
            if (systemYamlDoc != null)
            {
                newConfigText = newConfigText + "\n" + "---\n" + systemYamlDoc;
            }

            if (Objects.equals(currentText, newConfigText))
            {
                break reconfigure;
            }

            logger.accept(newConfigText);

            newConfig = parse(newConfigText);

            if (newConfig != null)
            {
                for (NamespaceConfig newNamespace : newConfig.namespaces)
                {
                    if (newNamespace.configAt >= nonLocalConfigAt)
                    {
                        break;
                    }

                    newNamespace.vaults.forEach(v -> v.local = true);
                }

                final String oldConfigText = currentText;
                final EngineConfig oldConfig = current;

                unregister(oldConfig);

                try
                {
                    currentText = newConfigText;
                    current = newConfig;

                    register(newConfig);
                }
                catch (Exception ex)
                {
                    context.onError(ex);

                    currentText = oldConfigText;
                    current = oldConfig;

                    register(oldConfig);

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
    }

    private String buildSystemNamespaceIfNecessary()
    {
        String systemYaml = null;
        try
        {
            JsonObject systemBase = Json.createObjectBuilder()
                .add("name", "system")
                .build();

            JsonObject systemPatched = systemBase;

            JsonProvider schemaProvider = JsonProvider.provider();

            for (URL patch : systemConfigs)
            {
                try (InputStream systemPatchInput = patch.openStream();
                     JsonReader systemPatchReader = schemaProvider.createReader(systemPatchInput))
                {
                    JsonArray systemPatchArray = systemPatchReader.readArray();
                    JsonPatch systemPatch = schemaProvider.createPatch(systemPatchArray);

                    systemPatched = systemPatch.apply(systemPatched);
                }
            }

            if (!systemPatched.equals(systemBase))
            {
                JsonbConfig config = new JsonbConfig()
                    .withAdapters(new NamespaceAdapter(null))
                    .withFormatting(true);
                Jsonb jsonb = JsonbBuilder.newBuilder()
                    .withProvider(schemaProvider)
                    .withConfig(config)
                    .build();

                NamespaceConfig namespace = jsonb.fromJson(systemPatched.toString(), NamespaceConfig.class);

                EngineConfigWriter writer = new EngineConfigWriter(null);
                systemYaml = writer.write(namespace);
            }
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }

        return systemYaml;
    }

    private EngineConfig parse(
        String configText)
    {
        EngineConfig engine = null;

        try
        {
            EngineConfigReader reader = new EngineConfigReader(
                config,
                new NamespaceConfigAdapterContext(Path.of(config.configURI())),
                expressions,
                schemaTypes,
                logger);

            engine = reader.read(configText);

            final List<GuardConfig> guards = engine.namespaces.stream()
                .map(n -> n.guards)
                .flatMap(gs -> gs.stream())
                .collect(toList());

            for (NamespaceConfig namespace : engine.namespaces)
            {
                process(guards, namespace);
            }
        }
        catch (Throwable ex)
        {
            rethrowUnchecked(ex);
        }

        return engine;
    }

    private void process(
        List<GuardConfig> guards,
        NamespaceConfig namespace)
    {
        namespace.id = supplyId.applyAsInt(namespace.name);

        NameResolver resolver = new NameResolver(namespace.id);

        for (GuardConfig guard : namespace.guards)
        {
            guard.id = resolver.resolve(guard.name);
        }

        for (VaultConfig vault : namespace.vaults)
        {
            vault.id = resolver.resolve(vault.name);
        }

        for (CatalogConfig catalog : namespace.catalogs)
        {
            catalog.id = resolver.resolve(catalog.name);
            if (catalog.vault != null)
            {
                catalog.vaultId = resolver.resolve(catalog.vault);
            }
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
                route.authorized = (session, resolve) -> true;

                if (route.guarded != null)
                {
                    for (GuardedConfig guarded : route.guarded)
                    {
                        guarded.id = resolver.resolve(guarded.name);

                        LongObjectPredicate<UnaryOperator<String>> authorizer = guards.stream()
                            .filter(g -> g.id == guarded.id)
                            .findFirst()
                            .map(g -> guardByType.apply(g.type))
                            .map(g -> g.verifier(EngineWorker::indexOfId, guarded))
                            .orElse((session, resolve) -> false);

                        LongFunction<String> identifier = guards.stream()
                            .filter(g -> g.id == guarded.id)
                            .findFirst()
                            .map(g -> guardByType.apply(g.type))
                            .map(g -> g.identifier(EngineWorker::indexOfId, guarded))
                            .orElse(session -> null);

                        guarded.identity = identifier;

                        LongObjectBiFunction<String, String> attributor = guards.stream()
                            .filter(g -> g.id == guarded.id)
                            .findFirst()
                            .map(g -> guardByType.apply(g.type))
                            .map(g -> g.attributor(EngineWorker::indexOfId, guarded))
                            .orElse((session, name) -> null);

                        guarded.attributes = attributor;

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
        EngineConfig config)
    {
        if (config != null)
        {
            for (NamespaceConfig namespace : config.namespaces)
            {
                register(namespace);
                watch(namespace);
            }
        }

        extensions.forEach(e -> e.onRegistered(context));
    }

    private void unregister(
        EngineConfig config)
    {
        if (config != null)
        {
            for (NamespaceConfig namespace : config.namespaces)
            {
                unwatch(namespace);
                unregister(namespace);
            }
        }

        extensions.forEach(e -> e.onUnregistered(context));
    }

    private void watch(
        NamespaceConfig namespace)
    {
        namespace.resources.stream()
            .forEach(watchTask::watch);
    }

    private void unwatch(
        NamespaceConfig namespace)
    {
        namespace.resources.stream()
            .forEach(watchTask::unwatch);
    }

    private void register(
        NamespaceConfig namespace)
    {
        boss.attach(namespace).join();

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
            boss.detach(namespace).join();

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
        private final Path configPath;

        NamespaceConfigAdapterContext(
            Path configPath)
        {
            this.configPath = configPath;
        }

        @Override
        public String readResource(
            String location)
        {
            String content = null;

            try
            {
                Path path = configPath.resolveSibling(location);
                content = Files.readString(path);
            }
            catch (IOException ex)
            {
                rethrowUnchecked(ex);
            }

            return content;
        }
    }

    private final class WatchTaskImpl extends EngineConfigWatchTask
    {
        WatchTaskImpl(
            EngineConfiguration config,
            EngineEventContext events,
            Path configPath)
        {
            super(config, events, configPath);
        }

        @Override
        protected void onPathChanged(
            Path watchedPath)
        {
            EngineManager.this.onPathChanged(watchedPath);
        }
    }
}
