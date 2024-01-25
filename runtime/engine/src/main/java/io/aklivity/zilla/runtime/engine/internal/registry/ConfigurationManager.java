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

import java.io.StringReader;
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
import java.util.regex.Pattern;

import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogConfig;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigReader;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.config.GuardedConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.ModelConfig;
import io.aklivity.zilla.runtime.engine.config.NamespaceConfig;
import io.aklivity.zilla.runtime.engine.config.RouteConfig;
import io.aklivity.zilla.runtime.engine.config.VaultConfig;
import io.aklivity.zilla.runtime.engine.expression.ExpressionResolver;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.internal.Tuning;
import io.aklivity.zilla.runtime.engine.internal.stream.NamespacedId;

public class ConfigurationManager
{
    private static final String CONFIG_TEXT_DEFAULT = "name: default\n";

    private final Collection<URL> schemaTypes;
    private final Function<String, Guard> guardByType;
    private final ToIntFunction<String> supplyId;
    private final IntFunction<ToIntFunction<KindConfig>> maxWorkers;
    private final Tuning tuning;
    private final Collection<DispatchAgent> dispatchers;
    private final Consumer<String> logger;
    private final EngineExtContext context;
    private final EngineConfiguration config;
    private final List<EngineExtSpi> extensions;
    private final BiFunction<URL, String, String> readURL;
    private final ExpressionResolver expressions;

    public ConfigurationManager(
        Collection<URL> schemaTypes,
        Function<String, Guard> guardByType,
        ToIntFunction<String> supplyId,
        IntFunction<ToIntFunction<KindConfig>> maxWorkers,
        Tuning tuning,
        Collection<DispatchAgent> dispatchers,
        Consumer<String> logger,
        EngineExtContext context,
        EngineConfiguration config,
        List<EngineExtSpi> extensions,
        BiFunction<URL, String, String> readURL)
    {
        this.schemaTypes = schemaTypes;
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
    }

    public NamespaceConfig parse(
        URL configURL,
        String configText)
    {
        NamespaceConfig namespace = null;
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

            ConfigReader reader = new ConfigReader(
                new NamespaceConfigAdapterContext(namespaceReadURL),
                schemaTypes,
                config.verboseSchema() ? logger : null);

            namespace = reader.read(new StringReader(configText));
            namespace.id = supplyId.applyAsInt(namespace.name);
            namespace.readURL = namespaceReadURL;

            // TODO: consider qualified name "namespace::name"
            final NamespaceConfig namespace0 = namespace;
            namespace.resolveId = name -> name != null ? NamespacedId.id(namespace0.id, supplyId.applyAsInt(name)) : 0L;

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

                if (binding.options != null)
                {
                    for (ModelConfig model : binding.options.models)
                    {
                        if (model.cataloged != null)
                        {
                            for (CatalogedConfig cataloged : model.cataloged)
                            {
                                cataloged.id = namespace.resolveId.applyAsLong(cataloged.name);
                            }
                        }
                    }
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
                                .map(g -> g.verifier(DispatchAgent::indexOfId, guarded))
                                .orElse(session -> false);

                            LongFunction<String> identifier = namespace.guards.stream()
                                .filter(g -> g.id == guarded.id)
                                .findFirst()
                                .map(g -> guardByType.apply(g.type))
                                .map(g -> g.identifier(DispatchAgent::indexOfId, guarded))
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
        catch (Throwable ex)
        {
            logError(ex.getMessage());
            Arrays.stream(ex.getSuppressed())
                .map(Throwable::getMessage)
                .forEach(logger);
        }

        return namespace;
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

    public void register(
        NamespaceConfig namespace)
    {
        dispatchers.stream()
            .map(d -> d.attach(namespace))
            .reduce(CompletableFuture::allOf)
            .ifPresent(CompletableFuture::join);
        extensions.forEach(e -> e.onRegistered(context));
    }

    public void unregister(
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

    private void logError(
        String message)
    {
        logger.accept("Configuration parsing error: " + message);
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
