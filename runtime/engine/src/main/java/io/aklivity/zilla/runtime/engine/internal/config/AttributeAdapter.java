package io.aklivity.zilla.runtime.engine.internal.config;

import java.util.Arrays;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.engine.config.AttributeConfig;

public class AttributeAdapter implements JsonbAdapter<AttributeConfig[], JsonObject>
{
    public JsonObject adaptToJson(
        AttributeConfig[] attributes)
    {
        JsonObjectBuilder object = Json.createObjectBuilder();
        Arrays.stream(attributes).forEach(attribute -> object.add(attribute.name, attribute.value));
        return object.build();
    }

    public AttributeConfig[] adaptFromJson(
        JsonObject jsonObject)
    {
        return jsonObject.entrySet().stream()
                .map(i -> new AttributeConfig(i.getKey(), asJsonString(i.getValue())))
                .toArray(AttributeConfig[]::new);
    }

    private static String asJsonString(
        JsonValue value)
    {
        return ((JsonString) value).getString();
    }
}
