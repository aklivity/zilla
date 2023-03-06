package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Arrays;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;

public class MetricRefAdapter implements JsonbAdapter<MetricRefConfig[], JsonArray>
{
    @Override
    public JsonArray adaptToJson(
        MetricRefConfig[] metricRefs)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        Arrays.stream(metricRefs).forEach(metricRef -> array.add(metricRef.name));
        return array.build();
    }

    @Override
    public MetricRefConfig[] adaptFromJson(
        JsonArray jsonArray)
    {
        return jsonArray.stream()
                .map(i -> new MetricRefConfig(asJsonString(i)))
                .toArray(MetricRefConfig[]::new);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
