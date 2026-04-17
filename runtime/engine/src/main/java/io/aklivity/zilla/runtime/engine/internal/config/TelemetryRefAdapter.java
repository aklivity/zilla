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
package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryRefConfigBuilder;

public class TelemetryRefAdapter implements JsonbAdapter<TelemetryRefConfig, JsonObject>
{
    private static final String METRICS_NAME = "metrics";
    private static final String ATTRIBUTES_NAME = "attributes";

    private final MetricRefAdapter metricRef;
    private final AttributeAdapter attribute;

    public TelemetryRefAdapter()
    {
        this.metricRef = new MetricRefAdapter();
        this.attribute = new AttributeAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        TelemetryRefConfig telemetryRef)
    {
        JsonObjectBuilder item = Json.createObjectBuilder();

        if (telemetryRef.metricRefs != null)
        {
            JsonArrayBuilder metricRefs = Json.createArrayBuilder();
            telemetryRef.metricRefs.stream().forEach(m -> metricRefs.add(metricRef.adaptToJson(m)));
            item.add(METRICS_NAME, metricRefs);
        }

        if (telemetryRef.attributes != null)
        {
            JsonObjectBuilder attributes = Json.createObjectBuilder();
            for (AttributeConfig a : telemetryRef.attributes)
            {
                Map.Entry<String, JsonValue> entry = attribute.adaptToJson(a);
                attributes.add(entry.getKey(), entry.getValue());
            }
            item.add(ATTRIBUTES_NAME, attributes);
        }

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

        if (object.containsKey(ATTRIBUTES_NAME))
        {
            object.getJsonObject(ATTRIBUTES_NAME).entrySet().stream()
                .map(attribute::adaptFromJson)
                .forEach(telemetry::attribute);
        }

        return telemetry.build();
    }
}
