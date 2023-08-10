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

import java.util.Arrays;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfigBuilder;

public class TelemetryAdapter implements JsonbAdapter<TelemetryConfig, JsonObject>
{
    private static final String ATTRIBUTES_NAME = "attributes";
    private static final String METRICS_NAME = "metrics";
    private static final String EXPORTERS_NAME = "exporters";

    private final AttributeAdapter attribute;
    private final MetricAdapter metric;
    private final ExporterAdapter exporter;

    public TelemetryAdapter(
        ConfigAdapterContext context)
    {
        this.attribute = new AttributeAdapter();
        this.metric = new MetricAdapter();
        this.exporter = new ExporterAdapter(context);
    }

    @Override
    public JsonObject adaptToJson(
        TelemetryConfig telemetry)
    {
        JsonObjectBuilder item = Json.createObjectBuilder();

        JsonObjectBuilder attributes = Json.createObjectBuilder();
        for (AttributeConfig a: telemetry.attributes)
        {
            Map.Entry<String, JsonValue> entry = attribute.adaptToJson(a);
            attributes.add(entry.getKey(), entry.getValue());
        }
        item.add(ATTRIBUTES_NAME, attributes);

        JsonArrayBuilder metricRefs = Json.createArrayBuilder();
        telemetry.metrics.stream().forEach(m -> metricRefs.add(metric.adaptToJson(m)));
        item.add(METRICS_NAME, metricRefs);

        JsonObject exporters = exporter.adaptToJson(telemetry.exporters.toArray(ExporterConfig[]::new));
        item.add(EXPORTERS_NAME, exporters);

        return item.build();
    }

    @Override
    public TelemetryConfig adaptFromJson(
        JsonObject object)
    {
        TelemetryConfigBuilder<TelemetryConfig> telemetry = TelemetryConfig.builder();

        if (object.containsKey(ATTRIBUTES_NAME))
        {
            object.getJsonObject(ATTRIBUTES_NAME).entrySet().stream()
                .map(attribute::adaptFromJson)
                .forEach(telemetry::attribute);
        }

        if (object.containsKey(METRICS_NAME))
        {
            object.getJsonArray(METRICS_NAME).stream()
                .map(metric::adaptFromJson)
                .forEach(telemetry::metric);
        }

        if (object.containsKey(EXPORTERS_NAME))
        {
            Arrays.stream(exporter.adaptFromJson(object.getJsonObject(EXPORTERS_NAME)))
                .forEach(telemetry::exporter);
        }

        return telemetry.build();
    }
}
