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

import java.util.Collection;

/**
 * Service provider interface for contributing {@link Metric} descriptors to an existing {@link MetricGroup}.
 * <p>
 * A metric group is created by a {@link MetricGroupFactorySpi} and resolves metric names by their
 * first dot-separated segment. Implementations of this interface are discovered via
 * {@link java.util.ServiceLoader}, registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.metrics.MetricGroupExtSpi},
 * and extend the group whose name matches {@link #type()}.
 * </p>
 * <p>
 * The extended group delegates {@link MetricGroup#supply(String)} to its contributors and includes
 * their names in {@link MetricGroup#metricNames()}, so wildcard selection of the group namespace
 * covers contributed metrics. A contributed name must be prefixed by the group name.
 * A contribution that redefines a name already defined by the group, or by another contribution
 * to the same group, is rejected when the group is created.
 * </p>
 *
 * @see MetricGroup
 * @see MetricGroupFactorySpi
 */
public interface MetricGroupExtSpi
{
    /**
     * Returns the type name of the metric group being extended, e.g. {@code "http"}.
     *
     * @return the extended metric group type name
     */
    String type();

    /**
     * Returns the set of metric names contributed by this extension.
     *
     * @return an unmodifiable collection of fully-qualified metric names
     */
    Collection<String> metricNames();

    /**
     * Returns the {@link Metric} descriptor for the given fully-qualified metric name,
     * or {@code null} if this extension does not contribute a metric with that name.
     *
     * @param name  the fully-qualified metric name
     * @return the {@link Metric} descriptor, or {@code null}
     */
    Metric supply(
        String name);
}
