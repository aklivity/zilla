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

import java.util.function.LongSupplier;

/**
 * Provides read access to collected metric values for exporters.
 * <p>
 * A {@code Collector} is supplied to {@link ExporterContext#attach} and gives the exporter
 * a snapshot view of all counter, gauge, and histogram values currently held by the engine.
 * Values are accessed by {@code (bindingId, metricId)} pairs; the complete set of active
 * id pairs is enumerated via the {@code *Ids()} methods.
 * </p>
 * <p>
 * Metric values are read via {@link java.util.function.LongSupplier} instances rather than
 * direct longs, allowing the engine to return live-reading suppliers backed by the underlying
 * storage without requiring a snapshot copy.
 * </p>
 *
 * @see ExporterContext
 */
public interface Collector
{
    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of the
     * counter metric identified by {@code (bindingId, metricId)}.
     *
     * @param bindingId  the binding id
     * @param metricId   the metric id
     * @return a supplier for the current counter value
     */
    LongSupplier counter(
        long bindingId,
        long metricId);

    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of the
     * gauge metric identified by {@code (bindingId, metricId)}.
     *
     * @param bindingId  the binding id
     * @param metricId   the metric id
     * @return a supplier for the current gauge value
     */
    LongSupplier gauge(
        long bindingId,
        long metricId);

    /**
     * Returns an array of {@link java.util.function.LongSupplier}s, one per histogram bucket,
     * for the histogram metric identified by {@code (bindingId, metricId)}.
     *
     * @param bindingId  the binding id
     * @param metricId   the metric id
     * @return an array of suppliers, one per histogram bucket
     */
    LongSupplier[] histogram(
        long bindingId,
        long metricId);

    /**
     * Returns all active {@code (bindingId, metricId)} pairs for counter metrics.
     * <p>
     * Each element of the outer array is a two-element {@code long[]} containing
     * {@code {bindingId, metricId}}.
     * </p>
     *
     * @return array of {@code {bindingId, metricId}} pairs for all active counters
     */
    long[][] counterIds();

    /**
     * Returns all active {@code (bindingId, metricId)} pairs for gauge metrics.
     *
     * @return array of {@code {bindingId, metricId}} pairs for all active gauges
     */
    long[][] gaugeIds();

    /**
     * Returns all active {@code (bindingId, metricId)} pairs for histogram metrics.
     *
     * @return array of {@code {bindingId, metricId}} pairs for all active histograms
     */
    long[][] histogramIds();
}
