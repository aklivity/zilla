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
package io.aklivity.zilla.runtime.engine.metrics;

import java.net.URL;
import java.util.Collection;

/**
 * A named collection of related {@link Metric} descriptors contributed by a single module.
 * <p>
 * Each metrics module (e.g., {@code metrics-http}, {@code metrics-kafka}, {@code metrics-grpc})
 * registers a {@code MetricGroup} that enumerates the metrics it provides. The engine discovers
 * metric groups via {@link MetricGroupFactorySpi} and uses them to resolve metric names
 * referenced in {@code zilla.yaml} telemetry configuration.
 * </p>
 *
 * @see Metric
 * @see MetricGroupFactorySpi
 */
public interface MetricGroup
{
    /**
     * Returns the name of this metric group, e.g. {@code "http"}, {@code "kafka"}.
     * <p>
     * Used as the namespace prefix for metric names within this group
     * (e.g., metrics in the {@code "http"} group are named {@code "http.*"}).
     * </p>
     *
     * @return the metric group name
     */
    String name();

    /**
     * Returns a URL pointing to the JSON schema for configuring metrics in this group,
     * or {@code null} if no schema is provided.
     *
     * @return the configuration schema URL, or {@code null}
     */
    URL type();

    /**
     * Returns the {@link Metric} descriptor for the given fully-qualified metric name,
     * or {@code null} if this group does not provide a metric with that name.
     *
     * @param name  the fully-qualified metric name (e.g., {@code "http.request.size"})
     * @return the {@link Metric} descriptor, or {@code null}
     */
    Metric supply(
        String name);

    /**
     * Returns the set of all metric names provided by this group.
     * <p>
     * Used by exporters and the engine to enumerate available metrics without resolving them.
     * </p>
     *
     * @return an unmodifiable collection of fully-qualified metric names
     */
    Collection<String> metricNames();
}
