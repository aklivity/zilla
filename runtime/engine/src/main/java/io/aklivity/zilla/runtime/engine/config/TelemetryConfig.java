/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.config;

import java.util.List;

public class TelemetryConfig
{
    public static final TelemetryConfig EMPTY = new TelemetryConfig(List.of(), List.of(), List.of());

    public final List<AttributeConfig> attributes;
    public final List<MetricConfig> metrics;
    public final List<ExporterConfig> exporters;

    public TelemetryConfig(
        List<AttributeConfig> attributes,
        List<MetricConfig> metrics,
        List<ExporterConfig> exporters)
    {
        this.attributes = attributes;
        this.metrics = metrics;
        this.exporters = exporters;
    }
}
