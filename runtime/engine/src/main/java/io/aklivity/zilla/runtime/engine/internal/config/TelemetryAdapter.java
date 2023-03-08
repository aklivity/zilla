package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;
import io.aklivity.zilla.runtime.engine.config.MetricConfig;
import io.aklivity.zilla.runtime.engine.config.TelemetryConfig;

public class TelemetryAdapter implements JsonbAdapter<TelemetryConfig, JsonObject>
{
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
        /*JsonObjectBuilder item = Json.createObjectBuilder();

        JsonObject attributes = attribute.adaptToJson(telemetry.attributes.toArray(AttributeConfig[]::new));
        item.add(ATTRIBUTES_NAME, attributes);

        JsonArray metrics = metric.adaptToJson(telemetry.metrics.toArray(MetricConfig[]::new));
        item.add(METRICS_NAME, metrics);

        return item.build();*/
        // TODO: Ati - handle the array thing here
        return null;
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
