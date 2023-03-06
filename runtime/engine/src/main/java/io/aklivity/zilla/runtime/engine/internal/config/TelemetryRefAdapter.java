package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Arrays;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
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
        JsonArray metricRefs = metricRef.adaptToJson(telemetryRef.metricRefs.toArray(MetricRefConfig[]::new));
        item.add(METRICS_NAME, metricRefs);
        return item.build();
    }

    @Override
    public TelemetryRefConfig adaptFromJson(
        JsonObject jsonObject)
    {
        List<MetricRefConfig> metricRefs = jsonObject.containsKey(METRICS_NAME)
                ? Arrays.asList(metricRef.adaptFromJson(jsonObject.getJsonArray(METRICS_NAME)))
                : List.of();
        return new TelemetryRefConfig(metricRefs);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
