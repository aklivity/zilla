package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Map;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;

public class AttributeAdapter implements JsonbAdapter<AttributeConfig, Map.Entry<String, JsonValue>>
{
    /*public JsonObject adaptToJson(
        AttributeConfig[] attributes)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        Arrays.stream(attributes).forEach(attribute -> object.add(attribute.name, attribute.value));
        return object.build();
    }*/

    @Override
    public Map.Entry<String, JsonValue> adaptToJson(
        AttributeConfig attributeConfig)
    {
        // TODO: Ati
        return null;
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
