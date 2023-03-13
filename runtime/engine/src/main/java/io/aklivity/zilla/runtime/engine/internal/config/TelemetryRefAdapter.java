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
