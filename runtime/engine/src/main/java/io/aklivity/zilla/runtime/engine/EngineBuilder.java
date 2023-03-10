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

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.agrona.ErrorHandler;

import io.aklivity.zilla.runtime.engine.binding.Binding;
import io.aklivity.zilla.runtime.engine.binding.BindingFactory;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardFactory;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroupFactory;
import io.aklivity.zilla.runtime.engine.metrics.Metrics;
import io.aklivity.zilla.runtime.engine.vault.Vault;
import io.aklivity.zilla.runtime.engine.vault.VaultFactory;

public class EngineBuilder
{
    private Configuration config;
    private ErrorHandler errorHandler;
    private Collection<EngineAffinity> affinities;

    EngineBuilder()
    {
        this.affinities = new LinkedHashSet<>();
    }

    public EngineBuilder config(
        Configuration config)
    {
        this.config = requireNonNull(config);
        return this;
    }

    public EngineBuilder affinity(
        String namespace,
        String binding,
        long mask)
    {
        affinities.add(new EngineAffinity(namespace, binding, mask));
        return this;
    }

    public EngineBuilder errorHandler(
        ErrorHandler errorHandler)
    {
        this.errorHandler = requireNonNull(errorHandler);
        return this;
    }

    public Engine build()
    {
        final EngineConfiguration config = new EngineConfiguration(this.config != null ? this.config : new Configuration());

        final Set<Binding> bindings = new LinkedHashSet<>();
        final BindingFactory bindingFactory = BindingFactory.instantiate();
        for (String name : bindingFactory.names())
        {
            Binding binding = bindingFactory.create(name, config);
            bindings.add(binding);
        }

        final Set<Guard> guards = new LinkedHashSet<>();
        final GuardFactory guardFactory = GuardFactory.instantiate();
        for (String name : guardFactory.names())
        {
            Guard guard = guardFactory.create(name, config);
            guards.add(guard);
        }

        final Set<Metrics> metricsSet = new LinkedHashSet<>();
        final MetricGroupFactory metricGroupFactory = MetricGroupFactory.instantiate();
        for (String name : metricGroupFactory.names())
        {
            Metrics metrics = metricGroupFactory.create(name, config);
            metricsSet.add(metrics);
        }

        final Set<Vault> vaults = new LinkedHashSet<>();
        final VaultFactory vaultFactory = VaultFactory.instantiate();
        for (String name : vaultFactory.names())
        {
            Vault vault = vaultFactory.create(name, config);
            vaults.add(vault);
        }

        final ErrorHandler errorHandler = requireNonNull(this.errorHandler, "errorHandler");

        return new Engine(config, bindings, guards, metricsSet, vaults, errorHandler, affinities);
    }
}
