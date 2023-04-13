package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.AbstractMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;

public class AttributeAdapter implements JsonbAdapter<AttributeConfig, Map.Entry<String, JsonValue>>
{
    @Override
    public Map.Entry<String, JsonValue> adaptToJson(
        AttributeConfig attributeConfig)
    {
        return new AbstractMap.SimpleEntry<>(attributeConfig.name, Json.createValue(attributeConfig.value));
    }

    @Override
    public AttributeConfig adaptFromJson(
        Map.Entry<String, JsonValue> entry)
    {
        return new AttributeConfig(entry.getKey(), asJsonString(entry.getValue()));
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
