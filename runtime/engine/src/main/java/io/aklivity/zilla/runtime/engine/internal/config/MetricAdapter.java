package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricConfig;

public class MetricAdapter implements JsonbAdapter<MetricConfig, JsonValue>
{
    /*@Override
    public JsonArray adaptToJson(
        MetricConfig[] metrics)
    {
        JsonArrayBuilder array = Json.createArrayBuilder();
        Arrays.stream(metrics).forEach(metric -> array.add(metric.name));
        return array.build();
    }*/

    @Override
    public JsonValue adaptToJson(
        MetricConfig config)
    {
        // TODO: Ati
        return null;
    }

    @Override
    public MetricConfig adaptFromJson(
        JsonValue jsonValue)
    {
        String name = asJsonString(jsonValue);
        String[] parts = name.split("\\.");
        String group = parts[0];
        return new MetricConfig(group, name);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }

}
