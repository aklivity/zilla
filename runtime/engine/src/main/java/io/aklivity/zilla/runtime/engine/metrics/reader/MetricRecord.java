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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import java.util.Map;

/**
 * A resolved metric identity, combining the namespaced binding address with the metric name.
 * <p>
 * {@code MetricRecord} instances are produced by the engine's metrics reader and consumed
 * by exporters (e.g., {@code exporter-prometheus}) to label exported metric values with
 * their fully-qualified binding and metric identifiers.
 * </p>
 * <p>
 * The concrete implementations {@code ScalarRecord} (for counters and gauges) and
 * {@code HistogramRecord} extend this interface with value-reading methods appropriate
 * to each metric kind.
 * </p>
 *
 * @see Collector
 */
public interface MetricRecord
{
    /**
     * Returns the namespaced binding id that uniquely identifies the binding within the engine.
     *
     * @return the binding id
     */
    long bindingId();

    /**
     * Returns the namespace component of the binding's qualified name.
     *
     * @return the namespace name string
     */
    String namespace();

    /**
     * Returns the local name component of the binding within its namespace.
     *
     * @return the binding name string
     */
    String binding();

    /**
     * Returns the fully-qualified metric name, e.g. {@code "http.request.size"}.
     *
     * @return the metric name
     */
    String metric();

    /**
     * Returns the attributes label id that encodes user-defined dimensions for this metric.
     *
     * @return the attributes id, or {@code 0} when no attributes are present
     */
    int attributesId();

    /**
     * Returns the resolved arbitrary attributes as a map of key-value pairs.
     * The attribute string is stored as a label (e.g., {@code "method=GET,path=/items"})
     * and parsed back into structured form for export.
     *
     * @return a map of attribute key-value pairs, empty if {@code attributesId} is {@code 0}
     */
    default Map<String, String> attributes()
    {
        return Map.of();
    }
}
