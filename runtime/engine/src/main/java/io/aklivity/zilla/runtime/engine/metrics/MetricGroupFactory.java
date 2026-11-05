/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.metrics;

import static java.util.Objects.requireNonNull;
import static java.util.ServiceLoader.load;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.aklivity.zilla.config.engine.factory.Factory;
import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.internal.metrics.ExtendedMetricGroup;

public final class MetricGroupFactory extends Factory
{
    private final Map<String, MetricGroupFactorySpi> factorySpis;
    private final Map<String, List<MetricGroupExtSpi>> extSpisByType;

    public static MetricGroupFactory instantiate()
    {
        return instantiate(load(MetricGroupFactorySpi.class), load(MetricGroupExtSpi.class));
    }

    static MetricGroupFactory instantiate(
        Iterable<MetricGroupFactorySpi> factorySpis,
        Iterable<MetricGroupExtSpi> extSpis)
    {
        Map<String, List<MetricGroupExtSpi>> extSpisByType = instantiate(extSpis).stream()
            .collect(Collectors.groupingBy(MetricGroupExtSpi::type));

        return instantiate(factorySpis, factoriesByType -> new MetricGroupFactory(factoriesByType, extSpisByType));
    }

    public Iterable<String> names()
    {
        return factorySpis.keySet();
    }

    public MetricGroup create(
        String type,
        Configuration config)
    {
        requireNonNull(type, "type");

        MetricGroupFactorySpi factorySpi = requireNonNull(factorySpis.get(type), () -> "Unrecognized metrics type: " + type);

        MetricGroup group = factorySpi.create(config);
        List<MetricGroupExtSpi> extSpis = extSpisByType.get(type);

        return extSpis != null ? new ExtendedMetricGroup(group, extSpis) : group;
    }

    private MetricGroupFactory(
        Map<String, MetricGroupFactorySpi> factorySpis,
        Map<String, List<MetricGroupExtSpi>> extSpisByType)
    {
        this.factorySpis = factorySpis;
        this.extSpisByType = extSpisByType;
    }
}
