/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.a2a.internal.search;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import io.aklivity.zilla.config.binding.a2a.A2aDiscoverySearchConfig;
import io.aklivity.zilla.config.binding.a2a.A2aSearchIndexConfig;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndex;
import io.aklivity.zilla.runtime.binding.a2a.search.A2aAgentSearchIndexFactorySpi;
import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Builds the runtime {@link A2aAgentSearchIndex} for a configured
 * {@code options.discovery.search}, dispatching each configured indexer to the
 * {@link A2aAgentSearchIndexFactorySpi} registered for its {@code type}, and composing more than
 * one behind {@link A2aAgentSearchComposite}.
 */
public final class A2aAgentSearchIndexFactory
{
    private final Map<String, A2aAgentSearchIndexFactorySpi> factoriesByType;

    public A2aAgentSearchIndexFactory()
    {
        this.factoriesByType = ServiceLoader
            .load(A2aAgentSearchIndexFactorySpi.class)
            .stream()
            .map(Supplier::get)
            .collect(toMap(A2aAgentSearchIndexFactorySpi::type, identity()));
    }

    public boolean registered(
        String type)
    {
        return factoriesByType.containsKey(type);
    }

    public A2aAgentSearchIndex create(
        EngineContext context,
        A2aDiscoverySearchConfig search)
    {
        A2aAgentSearchIndex result = null;

        if (search != null && search.indexes != null && !search.indexes.isEmpty())
        {
            List<A2aAgentSearchIndex> indexes = new ArrayList<>();
            for (A2aSearchIndexConfig config : search.indexes)
            {
                A2aAgentSearchIndexFactorySpi factory = factoriesByType.get(config.type);
                if (factory != null)
                {
                    indexes.add(factory.create(context, config, search.fields, search.weights));
                }
            }

            if (!indexes.isEmpty())
            {
                result = indexes.size() == 1 ? indexes.get(0) : new A2aAgentSearchComposite(indexes);
            }
        }

        return result;
    }
}
