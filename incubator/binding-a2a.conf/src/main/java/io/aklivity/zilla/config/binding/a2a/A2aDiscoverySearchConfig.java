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

import static java.util.function.Function.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.aklivity.zilla.config.engine.Config;

public final class A2aDiscoverySearchConfig extends Config.Extensible
{
    public final int limit;
    public final List<String> fields;
    public final Map<String, Double> weights;
    public final List<A2aSearchIndexConfig> indexes;

    public static A2aDiscoverySearchConfigBuilder<A2aDiscoverySearchConfig> builder()
    {
        return new A2aDiscoverySearchConfigBuilder<>(identity());
    }

    public static <T> A2aDiscoverySearchConfigBuilder<T> builder(
        Function<A2aDiscoverySearchConfig, T> mapper)
    {
        return new A2aDiscoverySearchConfigBuilder<>(mapper);
    }

    A2aDiscoverySearchConfig(
        int limit,
        List<String> fields,
        Map<String, Double> weights,
        List<A2aSearchIndexConfig> indexes)
    {
        super(null, withIndexes(indexes));
        this.limit = limit;
        this.fields = fields;
        this.weights = weights;
        this.indexes = indexes;
    }

    private static List<Config.Reference> withIndexes(
        List<A2aSearchIndexConfig> indexes)
    {
        final List<Config.Reference> refs = new ArrayList<>();

        if (indexes != null)
        {
            indexes.forEach(index -> refs.addAll(index.refs()));
        }

        return refs;
    }
}
