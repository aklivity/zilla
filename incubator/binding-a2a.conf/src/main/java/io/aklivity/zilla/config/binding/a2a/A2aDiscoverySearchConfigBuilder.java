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
package io.aklivity.zilla.config.binding.a2a;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.ConfigBuilder;

public final class A2aDiscoverySearchConfigBuilder<T> extends ConfigBuilder<T, A2aDiscoverySearchConfigBuilder<T>>
{
    private final Function<A2aDiscoverySearchConfig, T> mapper;

    private int limit = -1;
    private List<String> fields;
    private Map<String, Double> weights;
    private List<A2aSearchIndexConfig> indexes;

    A2aDiscoverySearchConfigBuilder(
        Function<A2aDiscoverySearchConfig, T> mapper)
    {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Class<A2aDiscoverySearchConfigBuilder<T>> thisType()
    {
        return (Class<A2aDiscoverySearchConfigBuilder<T>>) getClass();
    }

    public A2aDiscoverySearchConfigBuilder<T> limit(
        int limit)
    {
        this.limit = limit;
        return this;
    }

    public A2aDiscoverySearchConfigBuilder<T> fields(
        List<String> fields)
    {
        this.fields = fields;
        return this;
    }

    public A2aDiscoverySearchConfigBuilder<T> weight(
        String field,
        double weight)
    {
        if (weights == null)
        {
            weights = new LinkedHashMap<>();
        }
        weights.put(field, weight);
        return this;
    }

    public A2aDiscoverySearchConfigBuilder<T> index(
        A2aSearchIndexConfig index)
    {
        if (indexes == null)
        {
            indexes = new ArrayList<>();
        }
        indexes.add(index);
        return this;
    }

    @Override
    public T build()
    {
        return mapper.apply(new A2aDiscoverySearchConfig(limit, fields, weights, indexes));
    }
}
