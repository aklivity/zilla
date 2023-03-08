package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;

public class MetricRefAdapter implements JsonbAdapter<MetricRefConfig, JsonValue>
{
    /*@Override
    public JsonArray adaptToJson(
        MetricRefConfig[] metricRefs)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        Arrays.stream(metricRefs).forEach(metricRef -> array.add(metricRef.name));
        return array.build();
    }*/

    @Override
    public JsonValue adaptToJson(
        MetricRefConfig metricRefConfig)
    {
        // TODO: Ati
        return null;
    }

    @Override
    public MetricRefConfig adaptFromJson(
        JsonValue jsonValue)
    {
        return new MetricRefConfig(asJsonString(jsonValue));
    }

    private static String asJsonString(
            JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
