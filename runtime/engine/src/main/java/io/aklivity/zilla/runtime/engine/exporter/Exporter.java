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

import java.net.URL;

import io.aklivity.zilla.runtime.engine.EngineContext;

/**
 * Entry point for a metrics and events exporter plugin.
 * <p>
 * An {@code Exporter} periodically reads collected metrics and emitted events from the engine
 * and forwards them to an external observability system. Built-in implementations include
 * {@code exporter-prometheus} (Prometheus scrape endpoint), {@code exporter-otlp}
 * (OpenTelemetry Protocol), and {@code exporter-stdout}.
 * </p>
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader} through {@link ExporterFactorySpi}.
 * </p>
 *
 * @see ExporterContext
 * @see ExporterHandler
 * @see ExporterFactorySpi
 */
public interface Exporter
{
    /**
     * Returns the unique name identifying this exporter type, e.g. {@code "prometheus"}.
     *
     * @return the exporter type name
     */
    String name();

    /**
     * Returns a URL pointing to the JSON schema for this exporter's configuration options.
     *
     * @return the configuration schema URL
     */
    URL type();

    /**
     * Returns a URL pointing to a system-level configuration schema applied engine-wide
     * when this exporter is active (e.g., enabling a Prometheus HTTP listener).
     * Returns {@code null} by default for exporters with no system-level side effects.
     *
     * @return the system schema URL, or {@code null}
     */
    default URL system()
    {
        return null;
    }

    /**
     * Creates a per-thread context for this exporter.
     *
     * @param context  the engine context for the calling I/O thread
     * @return a new {@link ExporterContext} confined to that thread
     */
    ExporterContext supply(
        EngineContext context);
}
