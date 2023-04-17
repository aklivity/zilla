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
package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfig;

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
        JsonObject jsonObject)
    {
        List<MetricRefConfig> metricRefs = jsonObject.containsKey(METRICS_NAME)
                ? jsonObject.getJsonArray(METRICS_NAME).stream()
                        .map(metricRef::adaptFromJson)
                        .collect(Collectors.toList())
                : List.of();
        return new TelemetryRefConfig(metricRefs);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
