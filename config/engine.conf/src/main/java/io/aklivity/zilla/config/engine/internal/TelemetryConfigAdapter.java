/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.engine.internal;

import static java.util.stream.Collectors.toMap;

import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import io.aklivity.zilla.config.engine.AttributeConfig;
import io.aklivity.zilla.config.engine.EngineInfo;
import io.aklivity.zilla.config.engine.ExporterConfig;
import io.aklivity.zilla.config.engine.ExporterInfo;
import io.aklivity.zilla.config.engine.TelemetryConfig;
import io.aklivity.zilla.config.engine.TelemetryConfigBuilder;

public class TelemetryConfigAdapter
{
    private static final String ATTRIBUTES_NAME = "attributes";
    private static final String METRICS_NAME = "metrics";
    private static final String EXPORTERS_NAME = "exporters";
    private static final String TYPE_NAME = "type";

    private final AttributeConfigAdapter attribute;
    private final MetricConfigAdapter metric;
    private final Map<String, ExporterConfigAdapter> exportersByType;

    public TelemetryConfigAdapter(
        EngineInfo info)
    {
        this.attribute = new AttributeConfigAdapter();
        this.metric = new MetricConfigAdapter();
        this.exportersByType = info.exporters().stream().collect(toMap(ExporterInfo::type, ExporterConfigAdapter::new));
    }

    public JsonObject adaptToJson(
        TelemetryConfig telemetry) throws Exception
    {
        JsonObjectBuilder item = Json.createObjectBuilder();

        JsonObjectBuilder attributes = Json.createObjectBuilder();
        for (AttributeConfig config : telemetry.attributes)
        {
            Map.Entry<String, JsonValue> entry = attribute.adaptToJson(config);
            String name = entry.getKey();
            JsonValue value = entry.getValue();
            attributes.add(name, value);
        }
        item.add(ATTRIBUTES_NAME, attributes);

        JsonArrayBuilder metricRefs = Json.createArrayBuilder();
        telemetry.metrics.stream().forEach(m -> metricRefs.add(metric.adaptToJson(m)));
        item.add(METRICS_NAME, metricRefs);

        JsonObjectBuilder exporters = Json.createObjectBuilder();
        for (ExporterConfig config : telemetry.exporters)
        {
            ExporterConfigAdapter adapter = exportersByType.get(config.type);
            assert adapter != null : "unrecognized exporter type: " + config.type;
            exporters.add(config.name, adapter.adaptToJson(config));
        }
        item.add(EXPORTERS_NAME, exporters);

        return item.build();
    }

    public TelemetryConfig adaptFromJson(
        String namespace,
        JsonObject object) throws Exception
    {
        TelemetryConfigBuilder<TelemetryConfig> telemetry = TelemetryConfig.builder()
            .namespace(namespace);

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
            for (Map.Entry<String, JsonValue> entry : object.getJsonObject(EXPORTERS_NAME).entrySet())
            {
                JsonObject item = entry.getValue().asJsonObject();
                String type = item.getString(TYPE_NAME);
                ExporterConfigAdapter adapter = exportersByType.get(type);
                assert adapter != null : "unrecognized exporter type: " + type;
                telemetry.exporter(adapter.adaptFromJson(namespace, entry.getKey(), item));
            }
        }

        return telemetry.build();
    }
}
