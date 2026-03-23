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
package io.aklivity.zilla.runtime.engine.exporter;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.factory.FactorySpi;

/**
 * Service provider interface for creating {@link Exporter} instances.
 * <p>
 * Implementations must be registered in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.exporter.ExporterFactorySpi}.
 * The {@link #type()} method returns the exporter type name that matches the {@code type}
 * field used in {@code zilla.yaml}.
 * </p>
 *
 * @see Exporter
 */
public interface ExporterFactorySpi extends FactorySpi
{
    /**
     * Returns the exporter type name, e.g. {@code "prometheus"}.
     *
     * @return the exporter type name
     */
    String type();

    /**
     * Creates a new {@link Exporter} instance for the given engine configuration.
     *
     * @param config  the engine configuration
     * @return a new {@link Exporter}
     */
    Exporter create(
        Configuration config);
}
