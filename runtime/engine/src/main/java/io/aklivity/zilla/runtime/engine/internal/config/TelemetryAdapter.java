package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.ExporterConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfig;

public class TelemetryAdapter implements JsonbAdapter<TelemetryConfig, JsonObject>
{
    private static final String ATTRIBUTES_NAME = "attributes";
    private static final String METRICS_NAME = "metrics";
    private static final String EXPORTERS_NAME = "exporters";

    private final AttributeAdapter attribute;
    private final MetricAdapter metric;
    private final ExporterAdapter exporter;

    public TelemetryAdapter()
    {
        this.attribute = new AttributeAdapter();
        this.metric = new MetricAdapter();
        this.exporter = new ExporterAdapter();
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
        JsonObject jsonObject)
    {
        List<AttributeConfig> attributes = jsonObject.containsKey(ATTRIBUTES_NAME)
                ? jsonObject.getJsonObject(ATTRIBUTES_NAME).entrySet().stream()
                        .map(attribute::adaptFromJson)
                        .collect(Collectors.toList())
                : List.of();
        List<MetricConfig> metrics = jsonObject.containsKey(METRICS_NAME)
                ? jsonObject.getJsonArray(METRICS_NAME).stream()
                        .map(metric::adaptFromJson)
                        .collect(Collectors.toList())
                : List.of();
        List<ExporterConfig> exporters = jsonObject.containsKey(EXPORTERS_NAME)
                ? Arrays.stream(exporter.adaptFromJson(jsonObject.getJsonObject(EXPORTERS_NAME)))
                        .collect(Collectors.toList())
                : List.of();

        return new TelemetryConfig(attributes, metrics, exporters);
    }
}
