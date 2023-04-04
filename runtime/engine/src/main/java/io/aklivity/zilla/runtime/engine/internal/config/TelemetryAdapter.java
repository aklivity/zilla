package io.aklivity.zilla.runtime.engine.internal.config;

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
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfig;

public class TelemetryAdapter implements JsonbAdapter<TelemetryConfig, JsonObject>
{
    public static final TelemetryConfig EMPTY_TELEMETRY_CONFIG = new TelemetryConfig(List.of(), List.of());

    private static final String ATTRIBUTES_NAME = "attributes";
    private static final String METRICS_NAME = "metrics";

    private final AttributeAdapter attribute;
    private final MetricAdapter metric;

    public TelemetryAdapter()
    {
        this.attribute = new AttributeAdapter();
        this.metric = new MetricAdapter();
    }

    @Override
    public JsonObject adaptToJson(
        TelemetryConfig telemetry)
    {
        JsonObjectBuilder item = Json.createObjectBuilder();

        JsonObjectBuilder attributes = Json.createObjectBuilder();
        for (AttributeConfig a : telemetry.attributes)
        {
            Map.Entry<String, JsonValue> entry = attribute.adaptToJson(a);
            attributes.add(entry.getKey(), entry.getValue());
        }
        item.add(ATTRIBUTES_NAME, attributes);

        JsonArrayBuilder metricRefs = Json.createArrayBuilder();
        telemetry.metrics.stream().forEach(m -> metricRefs.add(metric.adaptToJson(m)));
        item.add(METRICS_NAME, metricRefs);
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
        return new TelemetryConfig(attributes, metrics);
    }
}
