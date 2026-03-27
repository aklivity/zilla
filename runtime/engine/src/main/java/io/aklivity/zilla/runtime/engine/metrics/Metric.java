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

import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Descriptor for a single observable metric exposed by a binding or engine component.
 * <p>
 * A {@code Metric} is a named, typed measurement that the engine collects and makes available
 * to exporters. Metrics are grouped into {@link MetricGroup}s (e.g., {@code metrics-http},
 * {@code metrics-kafka}) and resolved by name via {@link EngineContext#resolveMetric(String)}.
 * </p>
 * <p>
 * Three kinds of metric are supported:
 * <ul>
 *   <li>{@link Kind#COUNTER} — a monotonically increasing value (e.g., total bytes received)</li>
 *   <li>{@link Kind#GAUGE} — a value that can increase and decrease (e.g., active connections)</li>
 *   <li>{@link Kind#HISTOGRAM} — a distribution of values bucketed by magnitude</li>
 * </ul>
 * </p>
 *
 * @see MetricGroup
 * @see MetricContext
 */
public interface Metric
{
    /**
     * The recording mechanism for a metric.
     */
    enum Kind
    {
        /** Monotonically increasing total, e.g. bytes sent, messages processed. */
        COUNTER,
        /** Point-in-time value that can rise or fall, e.g. active stream count. */
        GAUGE,
        /** Distribution of observed values across magnitude-based buckets. */
        HISTOGRAM
    }

    /**
     * The unit of measurement for a metric's recorded values.
     */
    enum Unit
    {
        /** Values represent byte counts. */
        BYTES,
        /** Values represent elapsed time in nanoseconds. */
        NANOSECONDS,
        /** Values represent dimensionless counts. */
        COUNT
    }

    /**
     * Returns the fully-qualified metric name, e.g. {@code "http.request.size"}.
     * <p>
     * The name is used in {@code zilla.yaml} telemetry configuration and as the metric
     * identifier passed to exporters.
     * </p>
     *
     * @return the metric name
     */
    String name();

    /**
     * Returns the recording kind for this metric.
     *
     * @return the metric kind
     */
    Kind kind();

    /**
     * Returns the unit of the values recorded by this metric.
     *
     * @return the metric unit
     */
    Unit unit();

    /**
     * Returns a human-readable description of what this metric measures, suitable for
     * display in exporter help text (e.g., Prometheus {@code # HELP} lines).
     *
     * @return the metric description
     */
    String description();

    /**
     * Creates a per-thread {@link MetricContext} that supplies the {@link MessageConsumer}
     * interceptor used to record values for this metric on the given I/O thread.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link MetricContext}
     */
    MetricContext supply(
        EngineContext context);
}
