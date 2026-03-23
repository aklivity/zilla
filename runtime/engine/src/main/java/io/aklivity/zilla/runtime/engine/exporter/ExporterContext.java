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

import java.util.List;
import java.util.function.LongFunction;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.KindConfig;
import io.aklivity.zilla.runtime.engine.metrics.Collector;

/**
 * Per-thread context for a metrics and events exporter.
 * <p>
 * Created once per I/O thread by {@link Exporter#supply(EngineContext)}. Manages the lifecycle
 * of {@link ExporterHandler} instances, each of which drives periodic export for a single
 * exporter configuration.
 * </p>
 *
 * @see Exporter
 * @see ExporterHandler
 */
public interface ExporterContext
{
    /**
     * Attaches an exporter configuration and returns a handler that will drive the export cycle.
     * <p>
     * The {@code collector} provides access to the current metric values. The {@code resolveKind}
     * function maps a binding id to its {@link KindConfig}, used to label exported metrics.
     * </p>
     *
     * @param config       the exporter configuration
     * @param attributes   the set of engine-level attributes to include as metric labels
     * @param collector    the metric value source to read from during export
     * @param resolveKind  function mapping a binding id to its {@link KindConfig}
     * @return a new {@link ExporterHandler} that drives the export cycle for this configuration
     */
    ExporterHandler attach(
        ExporterConfig config,
        List<AttributeConfig> attributes,
        Collector collector,
        LongFunction<KindConfig> resolveKind);

    /**
     * Detaches a previously attached exporter, releasing any associated resources.
     *
     * @param exporterId  the id of the exporter configuration to detach
     */
    void detach(
        long exporterId);
}
