/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;

public class TelemetryRefAdapter implements JsonbAdapter<TelemetryRefConfig, JsonObject>
{
    private static final String METRICS_NAME = "metrics";

    private final MetricRefAdapter metricRef;

    public TelemetryRefAdapter()
    {
        this.metricRef = new MetricRefAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        TelemetryRefConfig telemetryRef)
    {
        JsonObjectBuilder item = Json.createObjectBuilder();
        JsonArrayBuilder metricRefs = Json.createArrayBuilder();
        telemetryRef.metricRefs.stream().forEach(m -> metricRefs.add(metricRef.adaptToJson(m)));
        item.add(METRICS_NAME, metricRefs);
        return item.build();
    }

    @Override
    public TelemetryRefConfig adaptFromJson(
        JsonObject object)
    {
        TelemetryRefConfigBuilder<TelemetryRefConfig> telemetry = TelemetryRefConfig.builder();

        if (object.containsKey(METRICS_NAME))
        {
            object.getJsonArray(METRICS_NAME).stream()
                .map(metricRef::adaptFromJson)
                .forEach(telemetry::metric);
        }

        return telemetry.build();
    }
}
