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
 * Values are accessed by {@code (bindingId, metricId, attributesId)} triples; the complete
 * set of active triples is enumerated via the {@code *Ids()} methods.
 * </p>
 *
 * @see ExporterContext
 */
public interface Collector
{
    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of the
     * counter metric identified by {@code (bindingId, metricId, attributesId)}.
     *
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id
     * @return a supplier for the current counter value
     */
    LongSupplier counter(
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns a {@link java.util.function.LongSupplier} that reads the current value of the
     * gauge metric identified by {@code (bindingId, metricId, attributesId)}.
     *
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id
     * @return a supplier for the current gauge value
     */
    LongSupplier gauge(
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns an array of {@link java.util.function.LongSupplier}s, one per histogram bucket,
     * for the histogram metric identified by {@code (bindingId, metricId, attributesId)}.
     *
     * @param bindingId     the namespaced binding id
     * @param metricId      the metric label id
     * @param attributesId  the attributes label id
     * @return an array of suppliers, one per histogram bucket
     */
    LongSupplier[] histogram(
        long bindingId,
        int metricId,
        int attributesId);

    /**
     * Returns all active {@code (bindingId, metricId, attributesId)} triples for counter metrics.
     * <p>
     * Each element of the outer array is a three-element {@code long[]} containing
     * {@code {bindingId, (long)metricId, (long)attributesId}}.
     * </p>
     *
     * @return array of {@code {bindingId, metricId, attributesId}} triples for all active counters
     */
    long[][] counterIds();

    /**
     * Returns all active {@code (bindingId, metricId, attributesId)} triples for gauge metrics.
     *
     * @return array of {@code {bindingId, metricId, attributesId}} triples for all active gauges
     */
    long[][] gaugeIds();

    /**
     * Returns all active {@code (bindingId, metricId, attributesId)} triples for histogram metrics.
     *
     * @return array of {@code {bindingId, metricId, attributesId}} triples for all active histograms
     */
    long[][] histogramIds();
}
