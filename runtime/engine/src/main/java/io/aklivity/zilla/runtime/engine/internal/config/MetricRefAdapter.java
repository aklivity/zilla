package io.aklivity.zilla.runtime.engine.internal.config;

import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.MetricRefConfig;

public class MetricRefAdapter implements JsonbAdapter<MetricRefConfig, JsonValue>
{
    @Override
    public JsonValue adaptToJson(
        MetricRefConfig metricRef)
    {
        return Json.createValue(metricRef.name);
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
