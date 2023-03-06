package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Arrays;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricConfig;

public class MetricAdapter implements JsonbAdapter<MetricConfig[], JsonArray>
{
    @Override
    public JsonArray adaptToJson(
        MetricConfig[] metrics)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        Arrays.stream(metrics).forEach(metric -> array.add(metric.name));
        return array.build();
    }

    @Override
    public MetricConfig[] adaptFromJson(
        JsonArray jsonArray)
    {
        return jsonArray.stream()
                .map(i -> new MetricConfig(asJsonString(i)))
                .toArray(MetricConfig[]::new);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
