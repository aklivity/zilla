/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.config.engine;

import static jakarta.json.stream.JsonGenerator.PRETTY_PRINTING;
import static java.util.Collections.singletonMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;

import io.aklivity.zilla.runtime.common.feature.FeatureFilter;

public final class EngineSchemaReader
{
    private static final JsonProvider SCHEMA_PROVIDER = JsonProvider.provider();

    private final EngineInfo info;

    public EngineSchemaReader(
        EngineInfo info)
    {
        this.info = info;
    }

    public JsonObject read() throws IOException
    {
        InputStream schemaInput = info.schema().openStream();

        JsonReader schemaReader = SCHEMA_PROVIDER.createReader(schemaInput);
        JsonObject schemaObject = schemaReader.readObject();

        for (URL schemaType : info.patches())
        {
            InputStream schemaPatchInput = schemaType.openStream();
            JsonReader schemaPatchReader = SCHEMA_PROVIDER.createReader(schemaPatchInput);
            JsonArray schemaPatchArray = schemaPatchReader.readArray();
            JsonPatch schemaPatch = SCHEMA_PROVIDER.createPatch(schemaPatchArray);

            schemaObject = schemaPatch.apply(schemaObject);
        }

        return schemaObject;
    }

    public static String write(
        JsonObject schemaObject)
    {
        final StringWriter out = new StringWriter();
        SCHEMA_PROVIDER
            .createGeneratorFactory(singletonMap(PRETTY_PRINTING, true))
            .createGenerator(out)
            .write(schemaObject)
            .close();

        return out.getBuffer().toString();
    }

    public JsonObject stripIncubating(
        JsonObject schemaObject)
    {
        return FeatureFilter.isIncubatorEnabled() ? schemaObject : stripIncubatingSchema(schemaObject);
    }

    JsonObject stripIncubatingSchema(
        JsonObject schemaObject)
    {
        Map<String, JsonValue> entries = new LinkedHashMap<>();
        Set<String> removedProperties = new HashSet<>();

        for (Map.Entry<String, JsonValue> entry : schemaObject.entrySet())
        {
            String name = entry.getKey();
            JsonValue value = entry.getValue();

            if ("properties".equals(name) && value.getValueType() == JsonValue.ValueType.OBJECT)
            {
                entries.put(name, stripIncubatingProperties(value.asJsonObject(), removedProperties));
            }
            else
            {
                stripIncubatingEntry(entries, name, value);
            }
        }

        JsonValue required = entries.get("required");
        if (required != null && required.getValueType() == JsonValue.ValueType.ARRAY && !removedProperties.isEmpty())
        {
            entries.put("required", stripIncubatingRequired(required.asJsonArray(), removedProperties));
        }

        JsonObjectBuilder builder = Json.createObjectBuilder();
        entries.forEach(builder::add);

        return builder.build();
    }

    private JsonObject stripIncubatingProperties(
        JsonObject properties,
        Set<String> removedProperties)
    {
        Map<String, JsonValue> entries = new LinkedHashMap<>();

        for (Map.Entry<String, JsonValue> property : properties.entrySet())
        {
            String name = property.getKey();
            JsonValue value = property.getValue();

            if (value.getValueType() == JsonValue.ValueType.OBJECT && isIncubating(value.asJsonObject()))
            {
                removedProperties.add(name);
            }
            else
            {
                stripIncubatingEntry(entries, name, value);
            }
        }

        JsonObjectBuilder builder = Json.createObjectBuilder();
        entries.forEach(builder::add);

        return builder.build();
    }

    private void stripIncubatingEntry(
        Map<String, JsonValue> entries,
        String name,
        JsonValue value)
    {
        if (value.getValueType() == JsonValue.ValueType.OBJECT)
        {
            JsonObject child = value.asJsonObject();
            if (!isIncubating(child))
            {
                entries.put(name, stripIncubatingSchema(child));
            }
        }
        else if (value.getValueType() == JsonValue.ValueType.ARRAY)
        {
            entries.put(name, stripIncubatingArray(value.asJsonArray()));
        }
        else
        {
            entries.put(name, value);
        }
    }

    private JsonArray stripIncubatingArray(
        JsonArray array)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        for (JsonValue item : array)
        {
            if (item.getValueType() == JsonValue.ValueType.OBJECT)
            {
                JsonObject child = item.asJsonObject();
                if (!isIncubating(child))
                {
                    builder.add(stripIncubatingSchema(child));
                }
            }
            else
            {
                builder.add(item);
            }
        }

        return builder.build();
    }

    private JsonArray stripIncubatingRequired(
        JsonArray required,
        Set<String> removedProperties)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        for (JsonValue item : required)
        {
            if (item.getValueType() != JsonValue.ValueType.STRING || !removedProperties.contains(((JsonString) item).getString()))
            {
                builder.add(item);
            }
        }

        return builder.build();
    }

    private boolean isIncubating(
        JsonObject node)
    {
        return node.getBoolean("x-incubating", false);
    }
}
