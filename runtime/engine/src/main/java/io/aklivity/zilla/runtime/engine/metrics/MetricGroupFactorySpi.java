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

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.FactorySpi;

/**
 * Service provider interface for creating {@link MetricGroup} instances.
 * <p>
 * Each metrics module (e.g., {@code metrics-http}, {@code metrics-kafka}) provides an
 * implementation registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.metrics.MetricGroupFactorySpi}.
 * The {@link #type()} name identifies the metric namespace prefix
 * (e.g., {@code "http"} for metrics named {@code "http.*"}).
 * </p>
 *
 * @see MetricGroup
 */
public interface MetricGroupFactorySpi extends FactorySpi
{
    /**
     * Returns the metric group type name, e.g. {@code "http"}, {@code "kafka"}.
     * <p>
     * Serves as both the {@link FactorySpi#type()} discriminator for discovery and the
     * namespace prefix used in metric names within this group.
     * </p>
     *
     * @return the metric group type name
     */
    String type();

    /**
     * Creates a new {@link MetricGroup} instance for the given engine configuration.
     *
     * @param config  the engine configuration
     * @return a new {@link MetricGroup}
     */
    MetricGroup create(
        Configuration config);
}
