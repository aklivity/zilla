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

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigException;
import io.aklivity.zilla.runtime.engine.config.EngineConfig;
import io.aklivity.zilla.runtime.engine.config.EngineConfigReader;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.expression.ExpressionResolver;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.layouts.BindingsLayout;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public class EngineManager
{
    private static final String CONFIG_TEXT_DEFAULT = "name: default\n";

    private static final Pattern PATTERN_NSNAME = Pattern.compile("(?:(?<namespace>[^\\:]+)\\:)?(?<name>[^\\:]+)");

    private final Collection<URL> schemaTypes;
    private final Function<String, Binding> bindingByType;
    private final Function<String, Guard> guardByType;
    private final ToIntFunction<String> supplyId;
    private final IntFunction<ToIntFunction<KindConfig>> maxWorkers;
    private final Tuning tuning;
    private final Collection<EngineWorker> dispatchers;
    private final Consumer<String> logger;
    private final EngineExtContext context;
    private final EngineConfiguration config;
    private final List<EngineExtSpi> extensions;
    private final BiFunction<URL, String, String> readURL;
    private final ExpressionResolver expressions;
    private final Matcher matchName;

    private EngineConfig current;

    public EngineManager(
        Collection<URL> schemaTypes,
        Function<String, Binding> bindingByType,
        Function<String, Guard> guardByType,
        ToIntFunction<String> supplyId,
        IntFunction<ToIntFunction<KindConfig>> maxWorkers,
        Tuning tuning,
        Collection<EngineWorker> dispatchers,
        Consumer<String> logger,
        EngineExtContext context,
        EngineConfiguration config,
        List<EngineExtSpi> extensions,
        BiFunction<URL, String, String> readURL)
    {
        this.schemaTypes = schemaTypes;
        this.bindingByType = bindingByType;
        this.guardByType = guardByType;
        this.supplyId = supplyId;
        this.maxWorkers = maxWorkers;
        this.tuning = tuning;
        this.dispatchers = dispatchers;
        this.logger = logger;
        this.context = context;
        this.config = config;
        this.extensions = extensions;
        this.readURL = readURL;
        this.expressions = ExpressionResolver.instantiate();
        this.matchName = PATTERN_NSNAME.matcher("");
    }

    public EngineConfig reconfigure(
        URL configURL,
        String configText)
    {
        EngineConfig newConfig = null;

        try
        {
            newConfig = parse(configURL, configText);
            if (newConfig != null)
            {
                final EngineConfig oldConfig = current;
                unregister(oldConfig);

                try
                {
                    // TODO: move bindings layout to manager?
                    writeBindingsLayout(newConfig);
                    register(newConfig);
                    current = newConfig;
                }
                catch (Exception ex)
                {
                    context.onError(ex);
                    writeBindingsLayout(newConfig);
                    register(oldConfig);

                    LangUtil.rethrowUnchecked(ex);
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
                throw new ConfigException("Engine configuration failed");
            }
        }

        return newConfig;
    }

    private void writeBindingsLayout(
        EngineConfig engine)
    {
        try (BindingsLayout layout = BindingsLayout.builder()
                .directory(config.directory())
                .build())
        {
            for (NamespaceConfig namespace : engine.namespaces)
            {
                for (BindingConfig binding : namespace.bindings)
                {
                    long typeId = namespace.resolveId.applyAsLong(binding.type);
                    long kindId = namespace.resolveId.applyAsLong(binding.kind.name().toLowerCase());
                    Binding typed = bindingByType.apply(binding.type);
                    long originTypeId = namespace.resolveId.applyAsLong(typed.originType(binding.kind));
                    long routedTypeId = namespace.resolveId.applyAsLong(typed.routedType(binding.kind));
                    layout.writeBindingInfo(binding.id, typeId, kindId, originTypeId, routedTypeId);
                }
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
    }

    private EngineConfig parse(
        URL configURL,
        String configText)
    {
        EngineConfig engine = null;

        if (configText == null || configText.isEmpty())
        {
            configText = CONFIG_TEXT_DEFAULT;
        }

        logger.accept(configText);

        if (config.configResolveExpressions())
        {
            configText = expressions.resolve(configText);
        }

        try
        {
            final Function<String, String> namespaceReadURL = l -> readURL.apply(configURL, l);

            EngineConfigReader reader = new EngineConfigReader(
                new NamespaceConfigAdapterContext(namespaceReadURL),
                schemaTypes,
                config.verboseSchema() ? logger : null);

            engine = reader.read(configText);

            for (NamespaceConfig namespace : engine.namespaces)
            {
                namespace.id = supplyId.applyAsInt(namespace.name);
                namespace.readURL = namespaceReadURL;

                final NamespaceConfig namespace0 = namespace;
                namespace.resolveId = name ->
                {
                    long id = 0L;

                    if (name != null && matchName.reset(name).matches())
                    {
                        String ns = matchName.group("namespace");
                        String n = matchName.group("name");

                        int nsid = ns != null ? supplyId.applyAsInt(ns) : namespace0.id;
                        int nid = supplyId.applyAsInt(n);

                        id = NamespacedId.id(nsid, nid);
                    }

                    return id;
                };

                for (GuardConfig guard : namespace.guards)
                {
                    guard.id = namespace.resolveId.applyAsLong(guard.name);
                    guard.readURL = namespace.readURL;
                }

                for (VaultConfig vault : namespace.vaults)
                {
                    vault.id = namespace.resolveId.applyAsLong(vault.name);
                }

                for (CatalogConfig catalog : namespace.catalogs)
                {
                    catalog.id = namespace.resolveId.applyAsLong(catalog.name);
                }

                for (MetricConfig metric : namespace.telemetry.metrics)
                {
                    metric.id = namespace.resolveId.applyAsLong(metric.name);
                }

                for (BindingConfig binding : namespace.bindings)
                {
                    binding.id = namespace.resolveId.applyAsLong(binding.name);
                    binding.entryId = namespace.resolveId.applyAsLong(binding.entry);
                    binding.resolveId = namespace.resolveId;

                    if (binding.vault != null)
                    {
                        binding.vaultId = namespace.resolveId.applyAsLong(binding.vault);
                    }

                    for (RouteConfig route : binding.routes)
                    {
                        route.id = namespace.resolveId.applyAsLong(route.exit);
                        route.authorized = session -> true;

                        if (route.guarded != null)
                        {
                            for (GuardedConfig guarded : route.guarded)
                            {
                                guarded.id = namespace.resolveId.applyAsLong(guarded.name);

                                LongPredicate authorizer = namespace.guards.stream()
                                    .filter(g -> g.id == guarded.id)
                                    .findFirst()
                                    .map(g -> guardByType.apply(g.type))
                                    .map(g -> g.verifier(EngineWorker::indexOfId, guarded))
                                    .orElse(session -> false);

                                LongFunction<String> identifier = namespace.guards.stream()
                                    .filter(g -> g.id == guarded.id)
                                    .findFirst()
                                    .map(g -> guardByType.apply(g.type))
                                    .map(g -> g.identifier(EngineWorker::indexOfId, guarded))
                                    .orElse(session -> null);

                                guarded.identity = identifier;

                                route.authorized = route.authorized.and(authorizer);
                            }
                        }
                    }

                    binding.metricIds = resolveMetricIds(namespace, binding);

                    long affinity = tuning.affinity(binding.id);

                    final long maxbits = maxWorkers.apply(binding.type.intern().hashCode()).applyAsInt(binding.kind);
                    for (int bitindex = 0; Long.bitCount(affinity) > maxbits; bitindex++)
                    {
                        affinity &= ~(1 << bitindex);
                    }

                    tuning.affinity(binding.id, affinity);
                }
            }
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return engine;
    }

    private long[] resolveMetricIds(
        NamespaceConfig namespace,
        BindingConfig binding)
    {
        if (binding.telemetryRef == null || binding.telemetryRef.metricRefs == null)
        {
            return new long[0];
        }

        Set<Long> metricIds = new HashSet<>();
        for (MetricRefConfig metricRef : binding.telemetryRef.metricRefs)
        {
            Pattern pattern = Pattern.compile(metricRef.name);
            for (MetricConfig metric : namespace.telemetry.metrics)
            {
                if (pattern.matcher(metric.name).matches())
                {
                    metricIds.add(namespace.resolveId.applyAsLong(metric.name));
                }
            }
        }
        return metricIds.stream().mapToLong(Long::longValue).toArray();
    }

    private void register(
        EngineConfig config)
    {
        if (config != null)
        {
            for (NamespaceConfig namespace : config.namespaces)
            {
                register(namespace);
            }
        }
    }

    private void unregister(
        EngineConfig config)
    {
        if (config != null)
        {
            for (NamespaceConfig namespace : config.namespaces)
            {
                unregister(namespace);
            }
        }
    }

    private void register(
        NamespaceConfig namespace)
    {
        dispatchers.stream()
            .map(d -> d.attach(namespace))
            .reduce(CompletableFuture::allOf)
            .ifPresent(CompletableFuture::join);
        extensions.forEach(e -> e.onRegistered(context));
    }

    private void unregister(
        NamespaceConfig namespace)
    {
        if (namespace != null)
        {
            dispatchers.stream()
                .map(d -> d.detach(namespace))
                .reduce(CompletableFuture::allOf)
                .ifPresent(CompletableFuture::join);
            extensions.forEach(e -> e.onUnregistered(context));
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
